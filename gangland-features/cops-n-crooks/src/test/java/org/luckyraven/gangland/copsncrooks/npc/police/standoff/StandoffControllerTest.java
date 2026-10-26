package org.luckyraven.gangland.copsncrooks.npc.police.standoff;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.StandoffSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PerimeterController;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.EvasionClock;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.npc.NpcPost;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link StandoffController}: the three triggers, who is posted, the radio cadence and the four ways it ends. Cops are
 * state-tracking mocks; the ring runs on a flat world (solid below y 64, air above).
 */
@DisplayName("StandoffController")
class StandoffControllerTest {

	private static final long SEC = 1000L;
	private static final long T0  = 1_000_000L;

	private final UUID                playerId   = UUID.randomUUID();
	private final Player              player     = mock(Player.class);
	private final World               world      = flatWorld();
	private final Location            centre     = new Location(world, 0, 64, 0);
	private final CopGroup            group      = mock(CopGroup.class);
	private final NpcSquad            squad      = mock(NpcSquad.class);
	private final CopRadio            radio      = mock(CopRadio.class);
	private final PerimeterController perimeter  = mock(PerimeterController.class);
	private final WantedStars         stars      = mock(WantedStars.class);
	@SuppressWarnings("unchecked")
	private final UserManager<Player> users      = mock(UserManager.class);
	@SuppressWarnings("unchecked")
	private final User<Player>        user       = mock(User.class);
	private final CopConfigProvider   provider   = mock(CopConfigProvider.class);
	private final AtomicLong          clock      = new AtomicLong(T0);
	private final AtomicBoolean       restrained = new AtomicBoolean();
	private final AtomicBoolean       ceaseFire  = new AtomicBoolean();
	private final AtomicLong          standoffUntil = new AtomicLong();
	private final List<CopNpc>        cops       = new ArrayList<>();
	private final Map<CopNpc, AtomicReference<CopState>> states = new HashMap<>();

	private StandoffSettings settings = StandoffSettings.DEFAULT;
	/** A member's own sighting holds the squad in contact; true by default, so the triggers can be exercised. */
	private final AtomicBoolean       inContact  = new AtomicBoolean(true);
	/** The squad's last-known spot; by default where the suspect stands. */
	private Supplier<Location>        lastKnown  = () -> player.getLocation();

	private final StandoffController controller;

	StandoffControllerTest() {
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getLocation()).thenReturn(centre.clone());
		when(group.getCops()).thenReturn(cops);
		when(group.getSquad()).thenReturn(squad);
		when(squad.isInContact()).thenAnswer(i -> inContact.get());
		when(squad.lastKnownLocation()).thenAnswer(i -> lastKnown.get());
		when(group.getTargetPlayerId()).thenReturn(playerId);
		when(group.isCeaseFire()).thenAnswer(i -> ceaseFire.get());
		doAnswer(i -> {
			standoffUntil.set(i.getArgument(0));
			return null;
		}).when(group).setStandoffUntil(anyLong());
		when(group.isStandoff(anyLong())).thenAnswer(i -> (long) i.getArgument(0) < standoffUntil.get());
		when(provider.getStandoffSettings()).thenAnswer(i -> settings);
		when(users.getUser(player)).thenReturn(user);
		controller = new StandoffController(() -> provider, radio, perimeter, stars, users, p -> restrained.get(),
		                                    clock::get);
	}

	// ── fixtures ──────────────────────────────────────────────────────────────

	/** A flat world: solid below y 64, air from 64 up; every chunk loaded and no lane blocked. */
	private static World flatWorld() {
		return world((x, z) -> true, (x, z) -> false);
	}

	private static World world(BiPredicate<Integer, Integer> loaded, BiPredicate<Integer, Integer> blocked) {
		World world = mock(World.class);
		when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(i -> loaded.test(i.getArgument(0), i.getArgument(1)));
		when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(i -> {
			Block block = mock(Block.class);
			when(block.isPassable()).thenReturn((int) i.getArgument(1) >= 64);
			return block;
		});
		when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER),
		                          anyBoolean())).thenAnswer(i -> {
			Location start = i.getArgument(0);
			return blocked.test(start.getBlockX(), start.getBlockZ()) ? mock(RayTraceResult.class) : null;
		});
		return world;
	}

	private CopNpc cop(CopState state, UUID target, double x, long unreachableMs) {
		CopNpc                    cop     = mock(CopNpc.class);
		AtomicReference<CopState> current = new AtomicReference<>(state);
		states.put(cop, current);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(new Location(world, x, 64, 0));
		when(cop.getEntity()).thenReturn(entity);
		when(cop.getTargetPlayerId()).thenReturn(target);
		when(cop.getGroup()).thenReturn(group);
		when(cop.isValid()).thenReturn(true);
		when(cop.millisUnreachable()).thenReturn(unreachableMs);
		when(cop.getCurrentState()).thenAnswer(i -> current.get());
		doAnswer(i -> {
			current.set(i.getArgument(0));
			return null;
		}).when(cop).transitionTo(any());
		cops.add(cop);
		return cop;
	}

	private CopNpc hunter(double x, long unreachableMs) {
		return cop(CopState.PURSUING, playerId, x, unreachableMs);
	}

	private CopState stateOf(CopNpc cop) {
		return states.get(cop).get();
	}

	/** n hunting cops, the first {@code stuck} of them unreachable for 10 s (the default threshold). */
	private List<CopNpc> hunters(int n, int stuck) {
		List<CopNpc> made = new ArrayList<>();
		for (int i = 0; i < n; i++)
			made.add(hunter(i + 1, i < stuck ? 10 * SEC : 0));
		return made;
	}

	private void tickAt(long offsetMs) {
		clock.set(T0 + offsetMs);
		controller.tick(player, group);
	}

	private long now() {
		return clock.get();
	}

	/** How many times the group was told a standoff window (a non-zero setStandoffUntil). */
	private long starts() {
		return mockingDetails(group).getInvocations().stream()
		                            .filter(i -> i.getMethod().getName().equals("setStandoffUntil") &&
		                                         (long) i.getArgument(0) != 0L).count();
	}

	private void assertTimersZeroed() {
		verify(group).setStandoffUntil(0L);
		verify(group).setFallBackUntil(0L);
	}

	/** The argument of the newest holdPendingUntil call, 0 when there was none. */
	private long lastHold() {
		long last = 0L;
		for (var i : mockingDetails(group).getInvocations())
			if (i.getMethod().getName().equals("holdPendingUntil"))
				last = i.getArgument(0);
		return last;
	}

	private long holdsAboveZero() {
		return mockingDetails(group).getInvocations().stream()
		                            .filter(i -> i.getMethod().getName().equals("holdPendingUntil") &&
		                                         (long) i.getArgument(0) > 0L).count();
	}

	private void startViaUnreachable() {
		hunters(3, 2);
		tickAt(0);
	}

	private NpcPost postOf(CopNpc cop) {
		ArgumentCaptor<NpcPost> post = ArgumentCaptor.forClass(NpcPost.class);
		verify(cop, times(1)).holdPost(post.capture());
		return post.getValue();
	}

	private static Map<String, String> seconds(int s) {
		return Map.of("seconds", String.valueOf(s));
	}

	// ── triggers ──────────────────────────────────────────────────────────────

	@Test
	@DisplayName("only PURSUING and COMBAT cops chasing him are posted (IDLE, someone else's POSTED, GUARDING, CUFFING never are)")
	void draftsOnlyHuntingCops() {
		CopNpc idle    = cop(CopState.IDLE, playerId, 1, 10 * SEC);
		CopNpc posted  = cop(CopState.POSTED, playerId, 2, 10 * SEC);
		CopNpc guard   = cop(CopState.GUARDING, playerId, 3, 10 * SEC);
		CopNpc cuffing = cop(CopState.CUFFING, UUID.randomUUID(), 4, 10 * SEC);
		CopNpc other   = cop(CopState.PURSUING, UUID.randomUUID(), 5, 10 * SEC);
		CopNpc a       = cop(CopState.PURSUING, playerId, 6, 10 * SEC);
		CopNpc b       = cop(CopState.COMBAT, playerId, 7, 10 * SEC);

		tickAt(0);

		for (CopNpc not : List.of(idle, posted, guard, cuffing, other))
			verify(not, never()).holdPost(any());
		verify(a).holdPost(any());
		verify(b).holdPost(any());
		assertEquals(CopState.POSTED, stateOf(a));
		assertEquals(CopState.POSTED, stateOf(b));
		assertEquals(CopState.IDLE, stateOf(idle));
		assertEquals(CopState.GUARDING, stateOf(guard));
		assertEquals(CopState.CUFFING, stateOf(cuffing));
	}

	@Test
	@DisplayName("cops that are not hunting him never count towards the unreachable majority")
	void nonHuntingUnreachable_noStart() {
		hunters(2, 0);
		cop(CopState.IDLE, playerId, 8, 20 * SEC);
		cop(CopState.GUARDING, playerId, 9, 20 * SEC);
		cop(CopState.PURSUING, UUID.randomUUID(), 10, 20 * SEC);

		tickAt(0);

		assertEquals(0, starts());
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("most hunting cops unreachable for 10 s start it: the three timers are set and the leader radios Standoff")
	void unreachableMajority10s_starts() {
		hunters(3, 2);

		tickAt(0);

		long until = now() + 45 * SEC;
		verify(group).setStandoffUntil(until);
		verify(group).setFallBackUntil(until);
		assertTrue(now() < lastHold() && lastHold() <= until, "rolling hold " + lastHold());
		verify(radio).sayFromLeader(group, "Standoff");
		assertTrue(controller.isActive(playerId));
	}

	@Test
	@DisplayName("radio-far S: an unreachable majority with no member contact and no fresh sighting is no standoff")
	void unreachableWithoutContactOrSighting_noStart() {
		inContact.set(false);
		when(squad.hasFreshSighting()).thenReturn(false);
		hunters(3, 2);

		tickAt(0);

		assertEquals(0, starts());
		verify(radio, never()).sayFromLeader(any(), anyString());
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("radio-far S: a fresh sighting without the contact edge still counts for the unreachable trigger")
	void unreachableWithFreshSighting_starts() {
		inContact.set(false);
		when(squad.hasFreshSighting()).thenReturn(true);
		hunters(3, 2);

		tickAt(0);

		verify(radio).sayFromLeader(group, "Standoff");
	}

	@Test
	@DisplayName("radio-far S: the ring is centred on the squad's last-known spot, not where the suspect really is now")
	void ring_centredOnTheLastKnownSpot() {
		when(player.getLocation()).thenReturn(new Location(world, 50, 64, 0));
		lastKnown = centre::clone;
		List<CopNpc> hunters = hunters(3, 3);

		tickAt(0);

		for (CopNpc cop : hunters) {
			NpcPost post = postOf(cop);
			assertEquals(12.0, post.anchor().distance(centre), 1.5);
			assertEquals(centre.getX(), post.watch().getX(), 0.01);
		}
	}

	@Test
	@DisplayName("radio-far S: no last-known spot, no ring; the standoff still flags and radios")
	void noLastKnownSpot_noPosts() {
		lastKnown = () -> null;
		List<CopNpc> hunters = hunters(3, 3);

		tickAt(0);

		for (CopNpc cop : hunters)
			verify(cop, never()).holdPost(any());
		verify(radio).sayFromLeader(group, "Standoff");
	}

	@Test
	@DisplayName("unreachable for 9.999 s is not yet unreachable")
	void unreachableJustUnderThreshold_noStart() {
		hunter(1, 10 * SEC - 1);
		hunter(2, 10 * SEC - 1);

		tickAt(0);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("one of three unreachable is a minority: nothing happens")
	void unreachableMinority_noStart() {
		hunters(3, 1);

		tickAt(0);

		assertEquals(0, starts());
		verify(radio, never()).sayFromLeader(any(), anyString());
	}

	@Test
	@DisplayName("exactly half unreachable is not a majority")
	void unreachableExactlyHalf_noStart() {
		hunters(4, 2);

		tickAt(0);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("a lone hunting cop, however stuck, never starts it (hunting >= 2)")
	void loneHunter_noStart() {
		hunters(1, 1);

		tickAt(0);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("a fresh sighting with a hopeless route starts it (NO_ROUTE)")
	void hopelessRouteInContact_starts() {
		List<CopNpc> hunters = hunters(3, 0);
		when(hunters.get(1).isNavigationHopeless()).thenReturn(true);
		when(squad.hasFreshSighting()).thenReturn(true);

		tickAt(0);

		verify(group).setStandoffUntil(now() + 45 * SEC);
		verify(radio).sayFromLeader(group, "Standoff");
	}

	@Test
	@DisplayName("a hopeless route without a fresh sighting is not a standoff")
	void hopelessRouteWithoutSighting_noStart() {
		List<CopNpc> hunters = hunters(3, 0);
		when(hunters.get(0).isNavigationHopeless()).thenReturn(true);
		when(squad.hasFreshSighting()).thenReturn(false);

		tickAt(0);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("a fresh sighting with every route fine is no standoff")
	void sightingWithoutHopelessRoute_noStart() {
		hunters(3, 0);
		when(squad.hasFreshSighting()).thenReturn(true);

		tickAt(0);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("a hopeless route of a cop that is not hunting him does not count")
	void hopelessRouteOfNonHunter_noStart() {
		hunters(2, 0);
		CopNpc idle = cop(CopState.IDLE, playerId, 9, 0);
		when(idle.isNavigationHopeless()).thenReturn(true);
		when(squad.hasFreshSighting()).thenReturn(true);

		tickAt(0);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("two cops down 3 blocks apart 20 s apart start it (MAN_DOWN)")
	void twoDeathsOneSpot30s_starts() {
		hunters(3, 0);
		clock.set(T0);
		controller.recordCasualty(playerId, new Location(world, 0, 64, 0));
		clock.set(T0 + 20 * SEC);
		controller.recordCasualty(playerId, new Location(world, 3, 64, 0));

		controller.tick(player, group);

		verify(group).setStandoffUntil(now() + 45 * SEC);
		verify(radio).sayFromLeader(group, "Standoff");
	}

	@Test
	@DisplayName("two cops down 20 blocks apart is no standoff")
	void twoDeathsFarApart_noStart() {
		hunters(3, 0);
		controller.recordCasualty(playerId, new Location(world, 0, 64, 0));
		controller.recordCasualty(playerId, new Location(world, 20, 64, 0));

		tickAt(0);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("two cops down more than 30 s apart is no standoff")
	void twoDeathsOutsideWindow_noStart() {
		hunters(3, 0);
		clock.set(T0);
		controller.recordCasualty(playerId, new Location(world, 0, 64, 0));
		clock.set(T0 + 31 * SEC);
		controller.recordCasualty(playerId, new Location(world, 1, 64, 0));

		controller.tick(player, group);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("one cop down is not enough")
	void oneDeath_noStart() {
		hunters(3, 0);
		controller.recordCasualty(playerId, new Location(world, 0, 64, 0));

		tickAt(0);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("casualties of another suspect's squad do not count for him")
	void casualtiesOfAnotherSuspect_ignored() {
		hunters(3, 0);
		UUID elsewhere = UUID.randomUUID();
		controller.recordCasualty(elsewhere, new Location(world, 0, 64, 0));
		controller.recordCasualty(elsewhere, new Location(world, 1, 64, 0));

		tickAt(0);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("casualties in different worlds are never 'at one spot'")
	void casualtiesInDifferentWorlds_noStart() {
		hunters(3, 0);
		controller.recordCasualty(playerId, new Location(world, 0, 64, 0));
		controller.recordCasualty(playerId, new Location(flatWorld(), 0, 64, 0));

		assertDoesNotThrow(() -> tickAt(0));

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("Man_Down_Count 3 needs three cops down together")
	void manDownCountIsConfigurable() {
		settings = new StandoffSettings(true, 45, 15, 10, 3, 30, 8.0, 4, 12.0, 1, 60);
		hunters(3, 0);
		controller.recordCasualty(playerId, new Location(world, 0, 64, 0));
		controller.recordCasualty(playerId, new Location(world, 1, 64, 0));
		tickAt(0);
		assertEquals(0, starts());

		controller.recordCasualty(playerId, new Location(world, 2, 64, 0));
		tickAt(1);

		assertEquals(1, starts());
	}

	// ── posts ─────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("at most Post_Count cops are posted, the nearest first, on a ring of Post_Radius around him watching him")
	void postsAtMostPostCount() {
		List<CopNpc> hunters = hunters(6, 6);

		tickAt(0);

		for (int i = 0; i < 4; i++) {
			CopNpc cop = hunters.get(i);
			assertEquals(CopState.POSTED, stateOf(cop), "cop " + i);
			NpcPost post = postOf(cop);
			assertEquals(12.0, post.anchor().distance(centre), 1.5, "anchor of cop " + i);
			assertEquals(centre.getX(), post.watch().getX(), 0.01);
			assertEquals(centre.getZ(), post.watch().getZ(), 0.01);
		}
		for (int i = 4; i < 6; i++) {
			verify(hunters.get(i), never()).holdPost(any());
			assertEquals(CopState.PURSUING, stateOf(hunters.get(i)));
		}
	}

	@Test
	@DisplayName("every post gets its own spot")
	void postsHaveDistinctAnchors() {
		List<CopNpc> hunters = hunters(4, 4);

		tickAt(0);

		long distinct = hunters.stream().map(c -> postOf(c).anchor().toVector()).distinct().count();
		assertEquals(4, distinct);
	}

	@Test
	@DisplayName("Post_Count 2 posts two of six")
	void postCountIsConfigurable() {
		settings = new StandoffSettings(true, 45, 15, 10, 2, 30, 8.0, 2, 12.0, 1, 60);
		List<CopNpc> hunters = hunters(6, 6);

		tickAt(0);

		long posted = hunters.stream().filter(c -> stateOf(c) == CopState.POSTED).count();
		assertEquals(2, posted);
	}

	@Test
	@DisplayName("an active search perimeter already holds the ring: no own posts, but the standoff still flags and radios")
	void perimeterActive_noOwnPosts() {
		when(perimeter.isActive(playerId)).thenReturn(true);
		List<CopNpc> hunters = hunters(3, 3);

		tickAt(0);

		for (CopNpc cop : hunters)
			verify(cop, never()).holdPost(any());
		verify(group).setStandoffUntil(now() + 45 * SEC);
		verify(group).setFallBackUntil(now() + 45 * SEC);
		verify(radio).sayFromLeader(group, "Standoff");
		verify(perimeter, never()).start(any(), any(), anyDouble(), anyInt());
	}

	@Test
	@DisplayName("a suspect in no world gets the flags and the radio but no posts, and nothing throws")
	void nullWorld_flagsWithoutPosts() {
		when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0));
		List<CopNpc> hunters = hunters(3, 3);

		assertDoesNotThrow(() -> tickAt(0));

		for (CopNpc cop : hunters)
			verify(cop, never()).holdPost(any());
		verify(group).setStandoffUntil(now() + 45 * SEC);
		assertTrue(controller.isActive(playerId));
	}

	@Test
	@DisplayName("a second tick during the standoff does not restart it")
	void secondTick_doesNotRestart() {
		startViaUnreachable();

		tickAt(1 * SEC);

		assertEquals(1, starts());
		verify(radio, times(1)).sayFromLeader(group, "Standoff");
	}

	@Test
	@DisplayName("a squad with no cops starts nothing")
	void emptyGroup_noStart() {
		hunters(3, 3);
		when(group.isEmpty()).thenReturn(true);

		tickAt(0);

		assertEquals(0, starts());
	}

	// ── radio ─────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("Negotiate every 15 s with the seconds left: 45, 30, 15 at 0, 15, 30 s")
	void negotiateEvery15s() {
		startViaUnreachable();
		tickAt(1 * SEC);
		verify(radio, times(1)).sayFromLeader(eq(group), eq("Negotiate"), anyMap());

		tickAt(15 * SEC);
		tickAt(30 * SEC);

		verify(radio).sayFromLeader(group, "Negotiate", seconds(45));
		verify(radio).sayFromLeader(group, "Negotiate", seconds(30));
		verify(radio).sayFromLeader(group, "Negotiate", seconds(15));
		verify(radio, times(3)).sayFromLeader(eq(group), eq("Negotiate"), anyMap());
	}

	@Test
	@DisplayName("Standoff comes before the first Negotiate")
	void standoffBeforeNegotiate() {
		startViaUnreachable();

		InOrder order = inOrder(radio);
		order.verify(radio).sayFromLeader(group, "Standoff");
		order.verify(radio).sayFromLeader(eq(group), eq("Negotiate"), anyMap());
	}

	@Test
	@DisplayName("Negotiate_Seconds 10 changes the cadence")
	void negotiateCadenceIsConfigurable() {
		settings = new StandoffSettings(true, 45, 10, 10, 2, 30, 8.0, 4, 12.0, 1, 60);
		startViaUnreachable();

		tickAt(10 * SEC);

		verify(radio).sayFromLeader(group, "Negotiate", seconds(35));
	}

	// ── ends ──────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("at 45 s he gains one CRIME star, the leader radios Standoff_Over, posts go back to PURSUING and the timers are zeroed")
	void timeout_raisesStarAndReleases() {
		List<CopNpc> hunters = hunters(3, 3);
		tickAt(0);

		tickAt(45 * SEC);

		verify(stars).raise(user, 1, WantedCause.CRIME);
		verify(radio).sayFromLeader(group, "Standoff_Over");
		for (CopNpc cop : hunters) {
			verify(cop).releasePost();
			assertEquals(CopState.PURSUING, stateOf(cop));
		}
		assertTimersZeroed();
		assertFalse(controller.isActive(playerId));
		verify(radio, never()).sayFromLeader(eq(group), eq("Negotiate"), eq(seconds(0)));
	}

	@Test
	@DisplayName("just before 45 s nothing ends yet")
	void justBeforeTimeout_stillActive() {
		startViaUnreachable();

		tickAt(45 * SEC - 1);

		verify(stars, never()).raise(any(), anyInt(), any());
		assertTrue(controller.isActive(playerId));
	}

	@Test
	@DisplayName("Stars_On_Timeout 0 raises no star but still ends")
	void timeoutWithZeroStars_noRaise() {
		settings = new StandoffSettings(true, 45, 15, 10, 2, 30, 8.0, 4, 12.0, 0, 60);
		startViaUnreachable();

		tickAt(45 * SEC);

		verify(stars, never()).raise(any(), anyInt(), any());
		verify(radio).sayFromLeader(group, "Standoff_Over");
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("an offline user gets no star but the standoff still ends cleanly")
	void timeoutWithNoUser_endsWithoutStar() {
		when(users.getUser(player)).thenReturn(null);
		startViaUnreachable();

		assertDoesNotThrow(() -> tickAt(45 * SEC));

		verify(stars, never()).raise(any(), anyInt(), any());
		assertTimersZeroed();
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("the timeout star is the only star: no drop, restore or decay call, and the controller holds no EvasionClock")
	void neverTouchesEvasionClock() {
		startViaUnreachable();
		tickAt(45 * SEC);

		verify(stars, never()).drop(any(), anyInt(), any());
		verify(stars, never()).restore(any(), anyInt());
		verify(stars, never()).installDecayPolicy(any());
		verify(perimeter, never()).start(any(), any(), anyDouble(), anyInt());
		verify(perimeter, never()).end(any(), any());
		for (Field field : StandoffController.class.getDeclaredFields())
			assertNotEquals(EvasionClock.class, field.getType(), "field " + field.getName());
		for (Constructor<?> ctor : StandoffController.class.getDeclaredConstructors())
			for (Class<?> type : ctor.getParameterTypes())
				assertNotEquals(EvasionClock.class, type);
	}

	@Test
	@DisplayName("crouching (cease-fire pose) during it ends it as SURRENDER: no star, no Standoff_Over, still none at 45 s")
	void surrenderPose_endsSurrender() {
		List<CopNpc> hunters = hunters(3, 3);
		tickAt(0);
		ceaseFire.set(true);

		tickAt(5 * SEC);

		for (CopNpc cop : hunters)
			assertEquals(CopState.PURSUING, stateOf(cop));
		assertTimersZeroed();
		verify(stars, never()).raise(any(), anyInt(), any());
		verify(radio, never()).sayFromLeader(group, "Standoff_Over");
		assertFalse(controller.isActive(playerId));

		tickAt(45 * SEC);
		verify(stars, never()).raise(any(), anyInt(), any());
	}

	@Test
	@DisplayName("a suspect already crouching never starts one")
	void surrenderPose_noStart() {
		ceaseFire.set(true);
		hunters(3, 3);

		tickAt(0);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("the chase ended it (clearCombatAlert zeroed the window): GONE, posts released, no star, not even at 45 s")
	void chaseClearedMidStandoff_endsGoneWithoutStar() {
		List<CopNpc> hunters = hunters(3, 3);
		tickAt(0);
		standoffUntil.set(0L);

		tickAt(5 * SEC);

		assertFalse(controller.isActive(playerId));
		for (CopNpc cop : hunters)
			assertEquals(CopState.PURSUING, stateOf(cop));
		verify(group).setFallBackUntil(0L);

		tickAt(45 * SEC);
		verify(stars, never()).raise(any(), anyInt(), any());
		verify(radio, never()).sayFromLeader(group, "Standoff_Over");
	}

	@Test
	@DisplayName("a different group for the same suspect (rejoin, new chase) ends the stale standoff without a star")
	void newGroup_endsStaleStandoffWithoutStar() {
		startViaUnreachable();
		CopGroup fresh = mock(CopGroup.class);
		when(fresh.getTargetPlayerId()).thenReturn(playerId);

		clock.set(T0 + 100 * SEC);
		controller.tick(player, fresh);

		verify(stars, never()).raise(any(), anyInt(), any());
		assertFalse(controller.isActive(playerId));
		verify(group).setStandoffUntil(0L);
	}

	@Test
	@DisplayName("the refill hold rolls: always ahead of now, never past the timeout, and an early end stops renewing it")
	void refillHeldWhileActiveThenFreed() {
		List<CopNpc> hunters = hunters(3, 3);
		tickAt(0);

		tickAt(30 * SEC);
		assertTrue(lastHold() > T0 + 30 * SEC, "hold at 30 s " + lastHold());

		tickAt(44 * SEC);
		assertTrue(lastHold() > T0 + 44 * SEC && lastHold() <= T0 + 45 * SEC, "hold at 44 s " + lastHold());

		long before = holdsAboveZero();
		clock.set(T0 + 44_500L);
		controller.onCopAttacked(hunters.get(0), player);
		controller.tick(player, group);

		assertEquals(before, holdsAboveZero());
	}

	@Test
	@DisplayName("hitting a cop ends it as ESCALATE: no star, posts released, timers zeroed")
	void hitEnds_escalate() {
		List<CopNpc> hunters = hunters(3, 3);
		tickAt(0);

		controller.onCopAttacked(hunters.get(0), player);

		verify(stars, never()).raise(any(), anyInt(), any());
		verify(radio, never()).sayFromLeader(group, "Standoff_Over");
		for (CopNpc cop : hunters)
			assertEquals(CopState.PURSUING, stateOf(cop));
		assertTimersZeroed();
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("a hit by someone with no standoff does nothing")
	void hitByUnrelatedPlayer_ignored() {
		List<CopNpc> hunters = hunters(3, 3);
		tickAt(0);
		Player stranger = mock(Player.class);
		when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());

		controller.onCopAttacked(hunters.get(0), stranger);

		assertTrue(controller.isActive(playerId));
		verify(group, never()).setStandoffUntil(0L);
	}

	@Test
	@DisplayName("a hit when no standoff is running does nothing and does not throw")
	void hitWithoutStandoff_ignored() {
		List<CopNpc> hunters = hunters(2, 0);

		assertDoesNotThrow(() -> controller.onCopAttacked(hunters.get(0), player));

		assertEquals(0, starts());
		verify(group, never()).setStandoffUntil(0L);
	}

	@Test
	@DisplayName("handcuffed during it ends it as SURRENDER: no star, posts released, timers zeroed")
	void restrained_endsSurrender() {
		List<CopNpc> hunters = hunters(3, 3);
		tickAt(0);
		restrained.set(true);

		tickAt(5 * SEC);

		verify(stars, never()).raise(any(), anyInt(), any());
		for (CopNpc cop : hunters)
			assertEquals(CopState.PURSUING, stateOf(cop));
		assertTimersZeroed();
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("a handcuffed suspect never starts one")
	void restrained_noStart() {
		restrained.set(true);
		hunters(3, 3);

		tickAt(0);

		assertEquals(0, starts());
	}

	@Test
	@DisplayName("his group gone ends it as GONE: state removed, posts released, no star, no exception")
	void groupGone_cleansUp() {
		List<CopNpc> hunters = hunters(3, 3);
		tickAt(0);

		assertDoesNotThrow(() -> controller.tick(player, null));

		assertFalse(controller.isActive(playerId));
		for (CopNpc cop : hunters)
			assertEquals(CopState.PURSUING, stateOf(cop));
		verify(stars, never()).raise(any(), anyInt(), any());
	}

	@Test
	@DisplayName("a null group with no standoff is a no-op")
	void nullGroupWithoutStandoff_noop() {
		assertDoesNotThrow(() -> controller.tick(player, null));

		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("Enabled false ends a running standoff (GONE, timers zeroed, no star) and starts none after")
	void disabled_endsAndNeverStarts() {
		startViaUnreachable();
		settings = new StandoffSettings(false, 45, 15, 10, 2, 30, 8.0, 4, 12.0, 1, 60);

		tickAt(1 * SEC);

		assertFalse(controller.isActive(playerId));
		assertTimersZeroed();
		verify(stars, never()).raise(any(), anyInt(), any());

		tickAt(200 * SEC);
		assertEquals(1, starts());
	}

	@Test
	@DisplayName("a cop that already left POSTED, or a dead cop, is not touched on release")
	void release_skipsForeignAndDeadPosts() {
		List<CopNpc> hunters = hunters(3, 3);
		tickAt(0);
		CopNpc moved = hunters.get(0);
		CopNpc dead  = hunters.get(1);
		states.get(moved).set(CopState.COMBAT);
		when(dead.isValid()).thenReturn(false);

		tickAt(45 * SEC);

		verify(moved, never()).releasePost();
		assertEquals(CopState.COMBAT, stateOf(moved));
		verify(dead, never()).releasePost();
		verify(hunters.get(2)).releasePost();
	}

	// ── cooldown ──────────────────────────────────────────────────────────────

	@Test
	@DisplayName("60 s cooldown: no new standoff before it, one after")
	void cooldownBlocksRetrigger() {
		startViaUnreachable();
		tickAt(45 * SEC); // timeout, cooldown runs to 105 s

		tickAt(104 * SEC);
		assertEquals(1, starts());
		assertFalse(controller.isActive(playerId));

		tickAt(105 * SEC);
		assertEquals(2, starts());
		assertTrue(controller.isActive(playerId));
		verify(radio, times(2)).sayFromLeader(group, "Standoff");
	}

	@Test
	@DisplayName("an escalate also starts the cooldown")
	void escalateStartsCooldown() {
		List<CopNpc> hunters = hunters(3, 3);
		tickAt(0);
		controller.onCopAttacked(hunters.get(0), player);

		tickAt(10 * SEC);

		assertFalse(controller.isActive(playerId));
		assertEquals(1, starts());
	}

	@Test
	@DisplayName("casualties are forgotten at the end: the old pair does not retrigger after the cooldown")
	void casualtiesClearedAtEnd() {
		settings = new StandoffSettings(true, 45, 15, 10, 2, 600, 8.0, 4, 12.0, 1, 60);
		hunters(3, 0);
		controller.recordCasualty(playerId, new Location(world, 0, 64, 0));
		controller.recordCasualty(playerId, new Location(world, 1, 64, 0));
		tickAt(0);
		tickAt(45 * SEC);
		assertFalse(controller.isActive(playerId));

		tickAt(106 * SEC);

		assertEquals(1, starts());
	}

	// ── config ────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("no loaded cop config means the shipped defaults (45 s timer)")
	void nullProvider_usesDefaults() {
		StandoffController bare = new StandoffController(() -> null, radio, perimeter, stars, users, p -> false,
		                                                 clock::get);
		hunters(3, 3);

		bare.tick(player, group);

		verify(group).setStandoffUntil(now() + 45 * SEC);
	}

	@Test
	@DisplayName("Timer_Seconds 20 shortens the standoff")
	void timerIsConfigurable() {
		settings = new StandoffSettings(true, 20, 15, 10, 2, 30, 8.0, 4, 12.0, 1, 60);
		hunters(3, 3);

		tickAt(0);

		verify(group).setStandoffUntil(now() + 20 * SEC);
	}

	@Test
	@DisplayName("Unreachable_Seconds 3 lowers the threshold")
	void unreachableThresholdIsConfigurable() {
		settings = new StandoffSettings(true, 45, 15, 3, 2, 30, 8.0, 4, 12.0, 1, 60);
		hunter(1, 3 * SEC);
		hunter(2, 3 * SEC);

		tickAt(0);

		assertEquals(1, starts());
	}

	@Test
	@DisplayName("a suspect who left the squad's world starts no standoff and nothing throws")
	void crossWorld_noStart() {
		Location other = new Location(flatWorld(), 0, 64, 0);
		when(player.getLocation()).thenReturn(other);
		hunters(3, 3);

		assertDoesNotThrow(() -> tickAt(0));

		assertEquals(0, starts());
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("forget(id) drops a running standoff without a cooldown, so the next chase can start one")
	void forget_dropsStateWithoutCooldown() throws Exception {
		hunters(3, 3);
		tickAt(0);
		assertTrue(controller.isActive(playerId));

		controller.getClass().getMethod("forget", UUID.class).invoke(controller, playerId);

		assertFalse(controller.isActive(playerId));
		long before = starts();
		tickAt(1 * SEC);
		assertEquals(before + 1, starts());
	}

	@Test
	@DisplayName("a standoff never starts while a squad cop is cuffing the suspect")
	void cuffingCop_blocksStart() {
		hunters(3, 2);
		cop(CopState.CUFFING, playerId, 9, 0);

		tickAt(0);

		assertEquals(0, starts());
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("a posted cop that sees the suspect reports the sighting to the squad and keeps its post")
	void postedCopSeesSuspect_reportsSightingKeepsPost() {
		List<CopNpc> made = hunters(3, 2);
		for (CopNpc c : made)
			when(c.canSee(any(Player.class), anyDouble())).thenReturn(true);
		tickAt(0);
		CopNpc posted = made.stream().filter(c -> stateOf(c) == CopState.POSTED).findFirst().orElseThrow();

		tickAt(SEC);

		verify(squad, atLeastOnce()).reportSighting(any(Location.class));
		assertEquals(CopState.POSTED, stateOf(posted));
		assertTrue(controller.isActive(playerId));
	}

	@Test
	@DisplayName("cnc-integ INT-5: PATROL, WARNING and OVERWATCH cops (0.20) are never drafted into a standoff")
	void integ5_standoffNeverDraftsPatrolWarningOverwatch() {
		CopNpc patrol    = cop(CopState.valueOf("PATROL"), playerId, 1, 10 * SEC);
		CopNpc warning   = cop(CopState.valueOf("WARNING"), playerId, 2, 10 * SEC);
		CopNpc overwatch = cop(CopState.valueOf("OVERWATCH"), playerId, 3, 10 * SEC);
		hunter(4, 10 * SEC);
		hunter(5, 10 * SEC);

		tickAt(0);

		for (CopNpc not : List.of(patrol, warning, overwatch)) {
			verify(not, never()).holdPost(any());
			verify(not, never()).transitionTo(CopState.POSTED);
		}
	}
}
