package org.luckyraven.gangland.copsncrooks.npc.police.breach;

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
import org.luckyraven.gangland.copsncrooks.npc.police.breach.BreachController.Flashbang;
import org.luckyraven.gangland.copsncrooks.npc.police.breach.BreachController.Surroundings;
import org.luckyraven.gangland.copsncrooks.npc.police.breach.DoorFinder.Door;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BreachSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcPost;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BreachController} (0.21.0 stack up and breach): who is drafted, when the stack goes, the entry order and
 * spacing, the flashbang, and the ways a stack ends early. Cops are state- and post-tracking mocks; the clock is
 * driven by hand; the world is a {@link Surroundings} mock (the door scan itself is {@link DoorFinderTest}).
 */
@DisplayName("BreachController")
class BreachControllerTest {

	private static final long SECOND = 1000L;

	private final UUID   playerId = UUID.randomUUID();
	private final Player player   = mock(Player.class);
	private final World  world    = mock(World.class);

	private final Location doorLoc = new Location(world, 4, 64, 0);
	private final Location cell    = new Location(world, 5.5, 64, 0.5);

	private final CopGroup                   group        = mock(CopGroup.class);
	private final NpcSquad                   squad        = mock(NpcSquad.class);
	private final List<CopNpc>               cops         = new ArrayList<>();
	private final CopRadio                   radio        = mock(CopRadio.class);
	private final CopConfigProvider          provider     = mock(CopConfigProvider.class);
	private final Surroundings               surroundings = mock(Surroundings.class);
	private final Flashbang                  flashbang    = mock(Flashbang.class);
	private final AtomicLong                 clock        = new AtomicLong(1_000_000L);
	private final AtomicInteger              level        = new AtomicInteger(3);
	private final AtomicBoolean              standoff     = new AtomicBoolean(false);
	private final AtomicReference<Location>  lastKnown    = new AtomicReference<>();

	private BreachSettings settings = BreachSettings.DEFAULT;

	private final Map<CopNpc, AtomicReference<CopState>> states = new HashMap<>();
	private final Map<CopNpc, AtomicReference<NpcPost>>  posts  = new HashMap<>();
	private final Map<CopNpc, AtomicReference<Location>> where  = new HashMap<>();

	private final BreachController controller;

	BreachControllerTest() {
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("Suspect");
		when(player.getLocation()).thenReturn(new Location(world, 0.5, 64, 0.5));
		lastKnown.set(new Location(world, 0.5, 64, 0.5));
		when(squad.lastKnownLocation()).thenAnswer(i -> {
			Location at = lastKnown.get();
			return at == null ? null : at.clone();
		});
		when(group.getCops()).thenReturn(cops);
		when(group.getSquad()).thenReturn(squad);
		when(group.getTargetPlayerId()).thenReturn(playerId);
		when(group.getLevel()).thenAnswer(i -> level.get());
		when(group.isStandoff(anyLong())).thenAnswer(i -> standoff.get());
		when(provider.getBreachSettings()).thenAnswer(i -> settings);
		when(surroundings.indoors(any(Location.class), anyInt())).thenReturn(true);
		when(surroundings.doorNear(any(Location.class), anyInt())).thenReturn(Optional.of(new Door(doorLoc, cell)));
		when(world.getPlayers()).thenReturn(new ArrayList<>());
		controller = new BreachController(() -> provider, radio, clock::get, surroundings, flashbang);
	}

	// ---- fixtures ----

	private CopNpc cop(String role, CopState state, UUID target, double x) {
		return cop(role, "Defender".equals(role) ? 0.5 : 0.0, state, target, x);
	}

	private CopNpc cop(String role, double blockFraction, CopState state, UUID target, double x) {
		CopNpc                    cop     = mock(CopNpc.class);
		AtomicReference<CopState> current = new AtomicReference<>(state);
		AtomicReference<NpcPost>  post    = new AtomicReference<>();
		AtomicReference<Location> at      = new AtomicReference<>(new Location(world, x, 64, 0.5));
		states.put(cop, current);
		posts.put(cop, post);
		where.put(cop, at);
		CopRole copRole = new CopRole(role, role, NpcFanPlacement.ANY, null, null, 1.0, null, 0, 0.0, 1.0, 0, null, blockFraction,
		                              blockFraction > 0 ? 90.0 : 0.0, false, false);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenAnswer(i -> at.get().clone());
		when(cop.getRole()).thenReturn(copRole);
		when(cop.getEntity()).thenReturn(entity);
		when(cop.getTargetPlayerId()).thenReturn(target);
		when(cop.isValid()).thenReturn(true);
		when(cop.getCurrentState()).thenAnswer(i -> current.get());
		when(cop.getPost()).thenAnswer(i -> post.get());
		doAnswer(i -> {
			current.set(i.getArgument(0));
			return null;
		}).when(cop).transitionTo(any());
		doAnswer(i -> {
			post.set(i.getArgument(0));
			return null;
		}).when(cop).holdPost(any());
		doAnswer(i -> {
			post.set(null);
			return null;
		}).when(cop).releasePost();
		cops.add(cop);
		return cop;
	}

	private CopNpc hunter(String role, double x) {
		return cop(role, CopState.PURSUING, playerId, x);
	}

	private CopState stateOf(CopNpc cop) {
		return states.get(cop).get();
	}

	private void arrive(CopNpc cop) {
		where.get(cop).set(new Location(world, 5.5, 64, 0.5));
	}

	private void away(CopNpc cop) {
		where.get(cop).set(new Location(world, 0.5, 64, 0.5));
	}

	private void arriveAll(List<CopNpc> stack) {
		stack.forEach(this::arrive);
	}

	private void tick() {
		controller.tick(player, group);
	}

	private void advance(long millis) {
		clock.addAndGet(millis);
	}

	/** How many times any cop was told to hold a post so far. */
	private int totalPosts() {
		int total = 0;
		for (CopNpc cop : cops) {
			ArgumentCaptor<NpcPost> post = ArgumentCaptor.forClass(NpcPost.class);
			verify(cop, atLeast(0)).holdPost(post.capture());
			total += post.getAllValues().size();
		}
		return total;
	}

	/** Three hunters (Assault, Defender, Commander), stacked on the first tick. */
	private List<CopNpc> stackThree() {
		CopNpc assault   = hunter("Assault", 3);
		CopNpc defender  = hunter("Defender", 4);
		CopNpc commander = hunter("Commander", 5);
		tick();
		return List.of(assault, defender, commander);
	}

	/** A player at {@code x}; when {@code blocked}, a ray from his eye hits a block. */
	private Player bystander(double x, boolean blocked) {
		Player other = mock(Player.class);
		when(other.getUniqueId()).thenReturn(UUID.randomUUID());
		when(other.isOnline()).thenReturn(true);
		when(other.getWorld()).thenReturn(world);
		when(other.getLocation()).thenReturn(new Location(world, x, 64, 0.5));
		when(other.getEyeLocation()).thenReturn(new Location(world, x, 65.62, 0.5));
		if (blocked) {
			when(world.rayTraceBlocks(argThat((Location l) -> l != null && l.getX() == x), any(Vector.class),
			                          anyDouble(), any(FluidCollisionMode.class), anyBoolean()))
					.thenReturn(mock(RayTraceResult.class));
		}
		return other;
	}

	// ---- start conditions ----

	@Test
	@DisplayName("only cops hunting the suspect (PURSUING or COMBAT) are drafted")
	void draftsOnlyHuntingCops() {
		CopNpc idle     = cop("Assault", CopState.IDLE, playerId, 1);
		CopNpc postedBy = cop("Assault", CopState.POSTED, playerId, 2);
		CopNpc guarding = cop("Assault", CopState.GUARDING, playerId, 3);
		CopNpc cuffing  = cop("Assault", CopState.CUFFING, playerId, 4);
		CopNpc other    = cop("Assault", CopState.PURSUING, UUID.randomUUID(), 5);
		CopNpc noTarget = cop("Assault", CopState.PURSUING, null, 5);
		CopNpc chaser   = cop("Assault", CopState.PURSUING, playerId, 6);
		CopNpc fighter  = cop("Assault", CopState.COMBAT, playerId, 7);

		tick();

		for (CopNpc ignored : List.of(idle, postedBy, guarding, cuffing, other, noTarget)) {
			verify(ignored, never()).holdPost(any());
		}
		assertEquals(CopState.IDLE, stateOf(idle));
		assertEquals(CopState.GUARDING, stateOf(guarding));
		assertEquals(CopState.CUFFING, stateOf(cuffing));
		verify(chaser).holdPost(any());
		verify(fighter).holdPost(any());
		assertEquals(CopState.POSTED, stateOf(chaser));
		assertEquals(CopState.POSTED, stateOf(fighter));
	}

	@Test
	@DisplayName("indoors with a door: the 3 hunters nearest the cell hold it, Stack_Up is radioed once")
	void indoorsWithDoor_stacksUpToStackSize() {
		List<CopNpc> five = new ArrayList<>();
		for (int i = 1; i <= 5; i++) five.add(hunter("Assault", i));

		tick();

		for (int i = 2; i < 5; i++) {
			ArgumentCaptor<NpcPost> post = ArgumentCaptor.forClass(NpcPost.class);
			verify(five.get(i)).holdPost(post.capture());
			assertEquals(cell.getBlockX(), post.getValue().anchor().getBlockX());
			assertEquals(cell.getBlockZ(), post.getValue().anchor().getBlockZ());
			assertNotNull(post.getValue().watch());
			assertEquals(doorLoc.getBlockX(), post.getValue().watch().getBlockX());
			assertEquals(1.5, post.getValue().leashRadius(), 1.0E-9);
			assertEquals(CopState.POSTED, stateOf(five.get(i)));
		}
		verify(five.get(0), never()).holdPost(any());
		verify(five.get(1), never()).holdPost(any());
		assertEquals(CopState.PURSUING, stateOf(five.get(0)));
		verify(radio, times(1)).sayFromLeader(group, "Stack_Up");
		assertTrue(controller.isActive(playerId));
	}

	@Test
	@DisplayName("a cop leaves its chase state before the post walk starts (the chase exit stops navigation)")
	void transitionToPostedBeforeHoldPost() {
		CopNpc only = hunter("Assault", 3);

		tick();

		InOrder order = inOrder(only);
		order.verify(only).transitionTo(CopState.POSTED);
		order.verify(only).holdPost(any());
	}

	@Test
	@DisplayName("Stack_Size is a setting: 1 drafts one cop")
	void stackSizeSetting() {
		settings = new BreachSettings(true, 2, 6, 1, 6, 20, 30, 4, 8.0, 40);
		CopNpc a = hunter("Assault", 5);
		CopNpc b = hunter("Assault", 4);

		tick();

		verify(a).holdPost(any());
		verify(b, never()).holdPost(any());
	}

	@Test
	@DisplayName("a missing config (null settings from the provider) falls back to the defaults and still stacks")
	void nullSettings_useDefaults() {
		when(provider.getBreachSettings()).thenReturn(null);
		CopNpc a = hunter("Assault", 5);

		tick();

		verify(a).holdPost(any());
	}

	@Test
	@DisplayName("Enabled false: nothing is ever drafted")
	void disabled_nothing() {
		settings = new BreachSettings(false, 2, 6, 3, 6, 20, 30, 4, 8.0, 40);
		CopNpc a = hunter("Assault", 5);

		tick();

		verify(a, never()).holdPost(any());
		verify(radio, never()).sayFromLeader(any(), any());
	}

	@Test
	@DisplayName("not indoors: nothing")
	void notIndoors_nothing() {
		when(surroundings.indoors(any(Location.class), anyInt())).thenReturn(false);
		CopNpc a = hunter("Assault", 5);

		tick();

		verify(a, never()).holdPost(any());
	}

	@Test
	@DisplayName("indoors but no door within Door_Radius: nothing; Roof_Blocks and Door_Radius are passed on")
	void noDoor_nothing() {
		when(surroundings.doorNear(any(Location.class), anyInt())).thenReturn(Optional.empty());
		CopNpc a = hunter("Assault", 5);

		tick();

		verify(a, never()).holdPost(any());
		verify(surroundings).doorNear(any(Location.class), eq(6));
		verify(surroundings).indoors(any(Location.class), eq(2));
	}

	@Test
	@DisplayName("no last-known spot yet: nothing")
	void noLastKnown_nothing() {
		lastKnown.set(null);
		CopNpc a = hunter("Assault", 5);

		tick();

		verify(a, never()).holdPost(any());
	}

	@Test
	@DisplayName("a last-known spot with no world (unloaded) is ignored without throwing")
	void lastKnownWorldNull_nothing() {
		lastKnown.set(new Location(null, 0, 64, 0));
		CopNpc a = hunter("Assault", 5);

		tick();

		verify(a, never()).holdPost(any());
		verify(surroundings, never()).indoors(any(Location.class), anyInt());
	}

	@Test
	@DisplayName("no squad group at all: nothing, no exception")
	void groupNull_nothing() {
		CopNpc a = hunter("Assault", 5);

		controller.tick(player, null);

		verify(a, never()).holdPost(any());
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("no hunting cop at all: no stack and no radio line")
	void noHunters_nothing() {
		cop("Assault", CopState.IDLE, playerId, 5);

		tick();

		verify(radio, never()).sayFromLeader(any(), any());
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("the world is scanned at most once a second per squad")
	void scansAreThrottled() {
		when(surroundings.indoors(any(Location.class), anyInt())).thenReturn(false);
		hunter("Assault", 5);

		tick();
		advance(300);
		tick();
		advance(300);
		tick();
		verify(surroundings, times(1)).indoors(any(Location.class), anyInt());

		advance(400);
		tick();
		verify(surroundings, times(2)).indoors(any(Location.class), anyInt());
	}

	@Test
	@DisplayName("no breach while the group is in a standoff; one starts once it is over")
	void noBreachDuringStandoff() {
		standoff.set(true);
		CopNpc a = hunter("Assault", 5);

		tick();
		advance(2 * SECOND);
		tick();

		verify(a, never()).holdPost(any());
		verify(radio, never()).sayFromLeader(any(), any());
		assertFalse(controller.isActive(playerId));

		standoff.set(false);
		advance(SECOND);
		tick();
		verify(a).holdPost(any());
	}

	// ---- go ----

	@Test
	@DisplayName("every stacker on its cell: Breach is radioed and the first cop is released the same tick")
	void goWhenAllArrived() {
		List<CopNpc> stack = stackThree();
		arriveAll(stack);

		advance(SECOND);
		tick();

		verify(radio, times(1)).sayFromLeader(group, "Breach");
		long released = stack.stream().filter(c -> stateOf(c) == CopState.PURSUING).count();
		assertEquals(1, released);
	}

	@Test
	@DisplayName("before Stack_Wait_Seconds with a stacker still walking: no Breach")
	void noGoBeforeWaitWhileWalking() {
		List<CopNpc> stack = stackThree();
		arrive(stack.get(0));
		arrive(stack.get(1));
		away(stack.get(2));

		advance(5 * SECOND);
		tick();

		verify(radio, never()).sayFromLeader(group, "Breach");
		for (CopNpc cop : stack) assertEquals(CopState.POSTED, stateOf(cop));
	}

	@Test
	@DisplayName("after 6 s with only 2 of 3 arrived: go anyway")
	void goAfterWaitEvenIfNotStacked() {
		List<CopNpc> stack = stackThree();
		arrive(stack.get(0));
		arrive(stack.get(1));
		away(stack.get(2));

		advance(6 * SECOND);
		tick();

		verify(radio, times(1)).sayFromLeader(group, "Breach");
	}

	@Test
	@DisplayName("the Defender goes in first, then the others one Entry_Gap_Ticks (20 ticks = 1 s) apart")
	void defenderEntersFirst_restOneSecondApart() {
		List<CopNpc> stack     = stackThree();
		CopNpc       assault   = stack.get(0);
		CopNpc       defender  = stack.get(1);
		CopNpc       commander = stack.get(2);
		arriveAll(stack);

		advance(SECOND);
		tick();
		assertEquals(CopState.PURSUING, stateOf(defender));
		assertEquals(CopState.POSTED, stateOf(assault));
		assertEquals(CopState.POSTED, stateOf(commander));
		verify(defender).releasePost();

		advance(SECOND - 1);
		tick();
		assertEquals(CopState.POSTED, stateOf(assault));
		assertEquals(CopState.POSTED, stateOf(commander));

		advance(1);
		tick();
		// the rest follow in draft order: the cop nearest the cell (the Commander at x 5) first
		assertEquals(CopState.PURSUING, stateOf(commander));
		assertEquals(CopState.POSTED, stateOf(assault));

		advance(SECOND);
		tick();
		assertEquals(CopState.PURSUING, stateOf(assault));
		verify(assault).releasePost();
		verify(commander).releasePost();
	}

	@Test
	@DisplayName("the shield role is found by Block_Fraction, not by name")
	void shieldRoleByBlockFraction_entersFirst() {
		CopNpc assault = hunter("Assault", 3);
		CopNpc riot    = cop("Riot", 0.6, CopState.PURSUING, playerId, 4);
		CopNpc named   = cop("Defender", 0.0, CopState.PURSUING, playerId, 5);
		tick();
		arriveAll(List.of(assault, riot, named));

		advance(SECOND);
		tick();

		assertEquals(CopState.PURSUING, stateOf(riot));
		assertEquals(CopState.POSTED, stateOf(assault));
		assertEquals(CopState.POSTED, stateOf(named));
	}

	@Test
	@DisplayName("with no Defender the squad leader goes first")
	void noDefender_leaderFirst() {
		CopNpc assault   = hunter("Assault", 3);
		CopNpc commander = hunter("Commander", 4);
		CopNpc marksman  = hunter("Marksman", 5);
		when(squad.leader()).thenReturn(commander);
		tick();
		arriveAll(List.of(assault, commander, marksman));

		advance(SECOND);
		tick();

		assertEquals(CopState.PURSUING, stateOf(commander));
		assertEquals(CopState.POSTED, stateOf(assault));
		assertEquals(CopState.POSTED, stateOf(marksman));
	}

	@Test
	@DisplayName("Entry_Gap_Ticks is a setting: 40 ticks spaces the entries 2 s apart")
	void entryGapSetting() {
		settings = new BreachSettings(true, 2, 6, 3, 6, 40, 30, 4, 8.0, 40);
		List<CopNpc> stack = stackThree();
		arriveAll(stack);

		advance(SECOND);
		tick();
		advance(SECOND);
		tick();
		// the Defender went at the go; the Commander (next in draft order) is 40 ticks later
		assertEquals(CopState.POSTED, stateOf(stack.get(2)));

		advance(SECOND);
		tick();
		assertEquals(CopState.PURSUING, stateOf(stack.get(2)));
	}

	// ---- flashbang ----

	@Test
	@DisplayName("at 4 stars: a player in range with a clear ray is flashed 40 ticks, one behind a wall or out of range is not")
	void flashbangAtFourStars_onlyWithLineToDoor() {
		level.set(4);
		Player clear   = bystander(6, false);
		Player behind  = bystander(8, true);
		Player farAway = bystander(40, false);
		when(world.getPlayers()).thenReturn(new ArrayList<>(List.of(clear, behind, farAway)));
		List<CopNpc> stack = stackThree();
		arriveAll(stack);

		advance(SECOND);
		tick();

		verify(flashbang).blind(clear, 40);
		verify(flashbang, never()).blind(eq(behind), anyInt());
		verify(flashbang, never()).blind(eq(farAway), anyInt());
		verify(radio, times(1)).sayFromLeader(group, "Flashbang");
	}

	@Test
	@DisplayName("the flashbang goes after Breach and before the first entry")
	void flashbangBeforeFirstEntry() {
		level.set(4);
		Player clear = bystander(6, false);
		when(world.getPlayers()).thenReturn(new ArrayList<>(List.of(clear)));
		List<CopNpc> stack    = stackThree();
		CopNpc       defender = stack.get(1);
		arriveAll(stack);

		advance(SECOND);
		tick();

		InOrder order = inOrder(radio, flashbang, defender);
		order.verify(radio).sayFromLeader(group, "Breach");
		order.verify(flashbang).blind(clear, 40);
		order.verify(radio).sayFromLeader(group, "Flashbang");
		order.verify(defender).releasePost();
	}

	@Test
	@DisplayName("at 3 stars nobody is flashed and no Flashbang line is radioed")
	void flashbangBelowMinStars_nobody() {
		level.set(3);
		Player clear = bystander(6, false);
		when(world.getPlayers()).thenReturn(new ArrayList<>(List.of(clear)));
		List<CopNpc> stack = stackThree();
		arriveAll(stack);

		advance(SECOND);
		tick();

		verify(flashbang, never()).blind(any(), anyInt());
		verify(radio, never()).sayFromLeader(group, "Flashbang");
		verify(radio).sayFromLeader(group, "Breach");
	}

	@Test
	@DisplayName("Flashbang.Min_Stars and Ticks are settings: 4 stars is not enough for 5, 60 ticks applied at 5")
	void flashbangSettings() {
		settings = new BreachSettings(true, 2, 6, 3, 6, 20, 30, 5, 8.0, 60);
		Player clear = bystander(6, false);
		when(world.getPlayers()).thenReturn(new ArrayList<>(List.of(clear)));
		level.set(4);
		List<CopNpc> stack = stackThree();
		arriveAll(stack);
		advance(SECOND);
		tick();
		verify(flashbang, never()).blind(any(), anyInt());

		controller.endAll();
		advance(60 * SECOND);
		level.set(5);
		tick();
		arriveAll(stack);
		advance(SECOND);
		tick();
		verify(flashbang).blind(clear, 60);
	}

	@Test
	@DisplayName("Flashbang.Radius is a setting: a player 3 blocks from the door is out of a radius-2 flash")
	void flashbangRadiusSetting() {
		settings = new BreachSettings(true, 2, 6, 3, 6, 20, 30, 4, 2.0, 40);
		level.set(4);
		Player near = bystander(7, false);
		when(world.getPlayers()).thenReturn(new ArrayList<>(List.of(near)));
		List<CopNpc> stack = stackThree();
		arriveAll(stack);

		advance(SECOND);
		tick();

		verify(flashbang, never()).blind(any(), anyInt());
	}

	@Test
	@DisplayName("applyFlashbang is safe in a JVM without a server (live proof: acceptance cnc21-BREACH-2)")
	void applyFlashbang_safeWithoutServer() {
		assertDoesNotThrow(() -> BreachController.applyFlashbang(mock(Player.class), 40));
	}

	@Test
	@DisplayName("no block is read for change, broken or placed during a full breach with a flashbang")
	void noBlockChanges() {
		level.set(4);
		Player clear = bystander(6, false);
		when(world.getPlayers()).thenReturn(new ArrayList<>(List.of(clear)));
		List<CopNpc> stack = stackThree();
		arriveAll(stack);
		for (int i = 0; i < 4; i++) {
			advance(SECOND);
			tick();
		}

		verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
		verify(world, never()).getBlockAt(any(Location.class));
		verify(world, never()).createExplosion(any(Location.class), anyFloat());
		for (CopNpc cop : stack) assertEquals(CopState.PURSUING, stateOf(cop));
	}

	// ---- aborts ----

	@Test
	@DisplayName("the last-known spot moving more than 8 blocks before the go releases every stacker to the chase")
	void abortWhenSuspectMoves() {
		List<CopNpc> stack = stackThree();
		lastKnown.set(new Location(world, 9.5, 64, 0.5));

		advance(SECOND);
		tick();

		for (CopNpc cop : stack) {
			assertEquals(CopState.PURSUING, stateOf(cop));
			verify(cop).releasePost();
		}
		verify(radio, never()).sayFromLeader(group, "Breach");
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("moving exactly 8 blocks is not enough to abort")
	void movingExactlyEight_keepsStack() {
		List<CopNpc> stack = stackThree();
		lastKnown.set(new Location(world, 8.5, 64, 0.5));

		advance(SECOND);
		tick();

		for (CopNpc cop : stack) assertEquals(CopState.POSTED, stateOf(cop));
		assertTrue(controller.isActive(playerId));
	}

	@Test
	@DisplayName("an abort starts the cooldown: no new stack for 30 s, then a new one")
	void abortSetsCooldown() {
		stackThree();
		lastKnown.set(new Location(world, 9.5, 64, 0.5));
		advance(SECOND);
		tick();
		lastKnown.set(new Location(world, 0.5, 64, 0.5));
		int before = totalPosts();

		advance(10 * SECOND);
		tick();
		assertEquals(before, totalPosts());

		advance(21 * SECOND);
		tick();
		assertEquals(before + 3, totalPosts());
	}

	@Test
	@DisplayName("the suspect no longer indoors before the go: everyone released")
	void abortWhenNoLongerIndoors() {
		List<CopNpc> stack = stackThree();
		when(surroundings.indoors(any(Location.class), anyInt())).thenReturn(false);

		advance(SECOND);
		tick();

		for (CopNpc cop : stack) assertEquals(CopState.PURSUING, stateOf(cop));
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("a standoff starting before the go releases the stack")
	void abortWhenStandoffStarts() {
		List<CopNpc> stack = stackThree();
		standoff.set(true);

		advance(SECOND);
		tick();

		for (CopNpc cop : stack) assertEquals(CopState.PURSUING, stateOf(cop));
		verify(radio, never()).sayFromLeader(group, "Breach");
	}

	@Test
	@DisplayName("the group vanishing mid-stack releases the stack")
	void abortWhenGroupGone() {
		List<CopNpc> stack = stackThree();

		advance(SECOND);
		controller.tick(player, null);

		for (CopNpc cop : stack) {
			assertEquals(CopState.PURSUING, stateOf(cop));
			verify(cop).releasePost();
		}
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("a stacker shot into COMBAT is dropped from the stack; the rest still go, and we never release it")
	void stackerShot_isDroppedOthersContinue() {
		List<CopNpc> stack = stackThree();
		CopNpc       shot  = stack.get(0);
		shot.transitionTo(CopState.COMBAT);
		arrive(stack.get(1));
		arrive(stack.get(2));

		advance(SECOND);
		tick();
		advance(2 * SECOND);
		tick();

		verify(radio, times(1)).sayFromLeader(group, "Breach");
		assertEquals(CopState.COMBAT, stateOf(shot));
		verify(shot, never()).releasePost();
		assertEquals(CopState.PURSUING, stateOf(stack.get(1)));
		assertEquals(CopState.PURSUING, stateOf(stack.get(2)));
	}

	@Test
	@DisplayName("every stacker dead or gone: the breach ends without a Breach call")
	void allStackersGone_endsQuietly() {
		List<CopNpc> stack = stackThree();
		for (CopNpc cop : stack) when(cop.isValid()).thenReturn(false);

		advance(SECOND);
		tick();

		assertFalse(controller.isActive(playerId));
		verify(radio, never()).sayFromLeader(group, "Breach");
	}

	@Test
	@DisplayName("the group vanishing mid-entry releases the cops still waiting")
	void groupGoneMidEntry_releasesTheRest() {
		List<CopNpc> stack = stackThree();
		arriveAll(stack);
		advance(SECOND);
		tick();

		controller.tick(player, null);

		for (CopNpc cop : stack) assertEquals(CopState.PURSUING, stateOf(cop));
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("endAll (reload, disable) releases every stacker and forgets the breach")
	void endAll_releasesEveryStacker() {
		List<CopNpc> stack = stackThree();

		controller.endAll();

		for (CopNpc cop : stack) {
			assertEquals(CopState.PURSUING, stateOf(cop));
			verify(cop).releasePost();
		}
		assertFalse(controller.isActive(playerId));
	}

	// ---- cooldown ----

	@Test
	@DisplayName("after the last entry the squad waits Cooldown_Seconds (30) before stacking again")
	void cooldownBlocksImmediateRestart() {
		List<CopNpc> stack = stackThree();
		arriveAll(stack);
		for (int i = 0; i < 4; i++) {
			advance(SECOND);
			tick();
		}
		for (CopNpc cop : stack) assertEquals(CopState.PURSUING, stateOf(cop));
		assertFalse(controller.isActive(playerId));
		int before = totalPosts();

		advance(28 * SECOND);
		tick();
		assertEquals(before, totalPosts());

		advance(SECOND);
		tick();
		assertEquals(before + 3, totalPosts());
	}

	// ---- judge fix r1 ----

	@Test
	@DisplayName("the wanted level ending while stacked: stand down, no Breach, no flashbang")
	void wantedEndBeforeGo_standsDownWithoutBreach() {
		level.set(4);
		Player clear = bystander(6, false);
		when(world.getPlayers()).thenReturn(new ArrayList<>(List.of(clear)));
		List<CopNpc> stack = stackThree();
		for (CopNpc cop : stack) when(cop.getTargetPlayerId()).thenReturn(null);
		arriveAll(stack);

		advance(SECOND);
		tick();

		verify(radio, never()).sayFromLeader(group, "Breach");
		verify(flashbang, never()).blind(any(), anyInt());
		for (CopNpc cop : stack) assertEquals(CopState.RETURNING, stateOf(cop));
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("a ray that only hits the door itself is clear (a door placed from inside has its panel on the near side)")
	void rayHittingTheDoorItselfIsClear() {
		level.set(4);
		Player inside = bystander(6, false);
		Block  panel  = mock(Block.class);
		when(panel.getX()).thenReturn(4);
		when(panel.getY()).thenReturn(65);
		when(panel.getZ()).thenReturn(0);
		RayTraceResult hit = mock(RayTraceResult.class);
		when(hit.getHitBlock()).thenReturn(panel);
		when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class),
		                          anyBoolean())).thenReturn(hit);
		when(world.getPlayers()).thenReturn(new ArrayList<>(List.of(inside)));
		List<CopNpc> stack = stackThree();
		arriveAll(stack);

		advance(SECOND);
		tick();

		verify(flashbang).blind(inside, 40);
	}

	@Test
	@DisplayName("Citizens player-type NPCs (the stack itself) are never flashed")
	void npcPlayersAreNotFlashed() {
		level.set(4);
		Player npc = bystander(6, false);
		when(npc.hasMetadata("NPC")).thenReturn(true);
		when(world.getPlayers()).thenReturn(new ArrayList<>(List.of(npc)));
		List<CopNpc> stack = stackThree();
		arriveAll(stack);

		advance(SECOND);
		tick();

		verify(flashbang, never()).blind(any(), anyInt());
	}

	@Test
	@DisplayName("a squad Defender farther than the nearest three still enters: it replaces the last drafted cop")
	void farDefender_isDrafted() {
		for (int i = 1; i <= 4; i++) hunter("Assault", i);
		CopNpc defender = hunter("Defender", 12);

		tick();

		verify(defender).holdPost(any());
		assertEquals(CopState.POSTED, stateOf(defender));
	}
}
