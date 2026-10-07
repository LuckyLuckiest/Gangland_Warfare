package org.luckyraven.gangland.copsncrooks.npc.police.perimeter;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PerimeterSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcPost;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PerimeterController}: who gets posted, where, and the four ways a perimeter ends. Cops are state-tracking mocks
 * (a {@code transitionTo} updates {@code getCurrentState}); the ring runs on {@link PostRingTest#flatWorld()}.
 */
@DisplayName("PerimeterController")
class PerimeterControllerTest {

	private final UUID          playerId = UUID.randomUUID();
	private final Player        player   = mock(Player.class);
	private final World         world    = PostRingTest.flatWorld();
	private final Location      centre   = new Location(world, 0, 64, 0);
	private final CopManager    manager  = mock(CopManager.class);
	private final CopRadio      radio    = mock(CopRadio.class);
	private final CopGroup      group    = mock(CopGroup.class);
	private final NpcSquad      squad    = mock(NpcSquad.class);
	private final List<CopNpc>  cops     = new ArrayList<>();
	private final AtomicLong    clock    = new AtomicLong(1_000_000L);
	private final CopConfigProvider provider = mock(CopConfigProvider.class);

	private PerimeterSettings settings = PerimeterSettings.DEFAULT;

	private final PerimeterController controller;

	PerimeterControllerTest() {
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getLocation()).thenReturn(centre.clone());
		when(manager.groupOf(playerId)).thenReturn(group);
		when(group.getCops()).thenReturn(cops);
		when(group.getSquad()).thenReturn(squad);
		when(provider.getPerimeterSettings()).thenAnswer(i -> settings);
		when(radio.placeOf(any())).thenReturn("the plaza");
		when(radio.compassWord(any(), any())).thenReturn("north");
		controller = new PerimeterController(() -> provider, manager, radio, clock::get);
	}

	private final Map<CopNpc, AtomicReference<CopState>> states = new java.util.HashMap<>();

	private CopNpc cop(String role, CopState state, UUID target, double x, double z) {
		CopNpc cop = mock(CopNpc.class);
		AtomicReference<CopState> current = new AtomicReference<>(state);
		states.put(cop, current);
		CopRole copRole = new CopRole(role, role, NpcFanPlacement.ANY, null, null, 1.0, null, 0, 0.0, 1.0, 0, null, 0.0,
		                              0.0, false, false);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(new Location(world, x, 64, z));
		when(cop.getRole()).thenReturn(copRole);
		when(cop.getEntity()).thenReturn(entity);
		when(cop.getTargetPlayerId()).thenReturn(target);
		when(cop.isValid()).thenReturn(true);
		when(cop.getCurrentState()).thenAnswer(i -> current.get());
		doAnswer(i -> {
			current.set(i.getArgument(0));
			return null;
		}).when(cop).transitionTo(any());
		cops.add(cop);
		return cop;
	}

	private CopNpc chaser(String role, double x) {
		return cop(role, CopState.PURSUING, playerId, x, 0);
	}

	private CopState stateOf(CopNpc cop) {
		return states.get(cop).get();
	}

	private List<NpcPost> postsOf(CopNpc cop, int times) {
		ArgumentCaptor<NpcPost> post = ArgumentCaptor.forClass(NpcPost.class);
		verify(cop, times(times)).holdPost(post.capture());
		return post.getAllValues();
	}

	@Test
	@DisplayName("a cop chasing someone else is never posted")
	void copChasingSomeoneElse_isNeverPosted() {
		CopNpc other  = cop("Marksman", CopState.PURSUING, UUID.randomUUID(), 1, 0);
		CopNpc civvie = cop("Defender", CopState.PURSUING, null, 2, 0);
		CopNpc a      = chaser("Assault", 3);
		CopNpc b      = chaser("Commander", 4);

		controller.start(player, centre, 90, 4);

		verify(other, never()).holdPost(any());
		verify(civvie, never()).holdPost(any());
		assertEquals(CopState.PURSUING, stateOf(other));
		// two eligible cops: one is posted, one stays free
		verify(a).holdPost(any());
		verify(b, never()).holdPost(any());
	}

	@Test
	@DisplayName("at three stars the Marksman and the Defender are posted ahead of the rest")
	void searchingAtThreeStars_postsMarksmanAndDefender() {
		CopNpc assault   = chaser("Assault", 1);
		CopNpc commander = chaser("Commander", 2);
		CopNpc marksman  = chaser("Marksman", 30);
		CopNpc defender  = chaser("Defender", 40);

		controller.start(player, centre, 90, 3);

		verify(marksman).holdPost(any());
		verify(defender).holdPost(any());
		verify(assault, never()).holdPost(any());
		verify(commander, never()).holdPost(any());
		assertEquals(CopState.POSTED, stateOf(marksman));
		assertEquals(CopState.POSTED, stateOf(defender));
		// the chase state's exit stops navigation: the post walk must start after it, or it is cancelled at once
		InOrder order = inOrder(marksman);
		order.verify(marksman).transitionTo(CopState.POSTED);
		order.verify(marksman).holdPost(any());
		assertTrue(controller.isActive(playerId));
		verify(radio).sayAs(eq(group), eq(marksman), eq("Post_Up"), eq(Map.of("place", "the plaza")));
		verify(radio).sayAs(eq(group), eq(defender), eq("Post_Up"), eq(Map.of("place", "the plaza")));
	}

	@Test
	@DisplayName("posts sit on a ring of 0.8 x Sight_Range even when the zone is 130 wide")
	void postsSitWithinSightOfTheCentre() {
		chaser("Commander", 1);
		CopNpc marksman = chaser("Marksman", 2);
		CopNpc defender = chaser("Defender", 3);

		controller.start(player, centre, 130, 4);

		for (CopNpc cop : List.of(marksman, defender)) {
			NpcPost post = postsOf(cop, 1).get(0);
			assertEquals(32.0, Math.hypot(post.anchor().getX(), post.anchor().getZ()), 1.0E-6);
			assertEquals(4.0, post.leashRadius(), 1.0E-6);
			assertEquals(0.0, post.watch().getX(), 1.0E-6);
		}
	}

	@Test
	@DisplayName("a small zone keeps its own radius")
	void smallZone_keepsItsRadius() {
		chaser("Commander", 1);
		CopNpc marksman = chaser("Marksman", 2);

		controller.start(player, centre, 20, 4);

		NpcPost post = postsOf(marksman, 1).get(0);
		assertEquals(20.0, Math.hypot(post.anchor().getX(), post.anchor().getZ()), 1.0E-6);
	}

	@Test
	@DisplayName("two stars is below Min_Level: no perimeter")
	void twoStars_noPerimeter() {
		CopNpc a = chaser("Marksman", 1);
		chaser("Defender", 2);
		chaser("Assault", 3);

		controller.start(player, centre, 90, 2);

		verify(a, never()).holdPost(any());
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("the last free cop is never posted: two cops post one, one cop posts none")
	void neverPostsTheLastFreeCop() {
		CopNpc marksman = chaser("Marksman", 1);
		CopNpc defender = chaser("Defender", 2);

		controller.start(player, centre, 90, 4);

		verify(marksman).holdPost(any());
		verify(defender, never()).holdPost(any());

		PerimeterController second = new PerimeterController(() -> provider, manager, radio, clock::get);
		cops.clear();
		CopNpc alone = chaser("Marksman", 1);
		second.start(player, centre, 90, 4);
		verify(alone, never()).holdPost(any());
		assertFalse(second.isActive(playerId));
	}

	@Test
	@DisplayName("a post that sees the suspect reports, radios Eyes_On and goes PURSUING")
	void postSeesTheSuspect_reportsRadiosAndPursues() {
		chaser("Commander", 1);
		CopNpc marksman = chaser("Marksman", 2);
		controller.start(player, centre, 90, 4);
		when(marksman.canSee(player, 40.0)).thenReturn(true);

		controller.tick(player, group);

		verify(squad).reportSighting(any());
		verify(radio).sayAs(eq(group), eq(marksman), eq("Eyes_On"),
		                    eq(Map.of("place", "the plaza", "direction", "north")));
		verify(marksman).releasePost();
		assertEquals(CopState.PURSUING, stateOf(marksman));
	}

	@Test
	@DisplayName("a post that cannot see him keeps holding and says nothing")
	void postWithoutSight_keepsHolding() {
		chaser("Commander", 1);
		CopNpc marksman = chaser("Marksman", 2);
		controller.start(player, centre, 90, 4);

		controller.tick(player, group);

		verify(squad, never()).reportSighting(any());
		verify(marksman, never()).releasePost();
		assertEquals(CopState.POSTED, stateOf(marksman));
		assertTrue(controller.isActive(playerId));
	}

	@Test
	@DisplayName("a post forced into COMBAT is dropped from the perimeter and left alone afterwards")
	void postForcedIntoCombat_isDroppedFromThePerimeter() {
		chaser("Commander", 1);
		CopNpc marksman = chaser("Marksman", 2);
		CopNpc defender = chaser("Defender", 3);
		controller.start(player, centre, 90, 4);
		states.get(marksman).set(CopState.COMBAT);

		controller.tick(player, group);
		controller.end(player, CopState.PURSUING);

		verify(marksman, never()).transitionTo(CopState.PURSUING);
		assertEquals(CopState.COMBAT, stateOf(marksman));
		assertEquals(CopState.PURSUING, stateOf(defender));
	}

	@Test
	@DisplayName("a dead post is dropped; the others keep holding")
	void deadPost_isDropped() {
		chaser("Commander", 1);
		CopNpc marksman = chaser("Marksman", 2);
		CopNpc defender = chaser("Defender", 3);
		controller.start(player, centre, 90, 4);
		when(marksman.isValid()).thenReturn(false);

		controller.tick(player, group);

		assertTrue(controller.isActive(playerId));
		assertEquals(CopState.POSTED, stateOf(defender));
		controller.end(player, CopState.PURSUING);
		verify(marksman, never()).transitionTo(CopState.PURSUING);
	}

	@Test
	@DisplayName("SEEN: every post is released and set PURSUING; ending twice is harmless")
	void seen_endsWithEveryPostPursuing() {
		chaser("Commander", 1);
		CopNpc marksman = chaser("Marksman", 2);
		CopNpc defender = chaser("Defender", 3);
		controller.start(player, centre, 90, 4);

		controller.end(player, CopState.PURSUING);
		controller.end(player, CopState.PURSUING);

		assertEquals(CopState.PURSUING, stateOf(marksman));
		assertEquals(CopState.PURSUING, stateOf(defender));
		verify(marksman).releasePost();
		verify(defender).releasePost();
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("OFF: the posts are sent home (RETURNING)")
	void off_sendsPostsHome() {
		chaser("Commander", 1);
		CopNpc marksman = chaser("Marksman", 2);
		controller.start(player, centre, 90, 4);

		controller.end(player, CopState.RETURNING);

		assertEquals(CopState.RETURNING, stateOf(marksman));
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("after Max_Seconds the perimeter ends with every post pursuing and stays spent until the next SEEN")
	void timeout_endsAndStaysSpentUntilSeen() {
		chaser("Commander", 1);
		CopNpc marksman = chaser("Marksman", 2);
		controller.start(player, centre, 90, 4);

		clock.addAndGet(59_000L);
		controller.tick(player, group);
		assertTrue(controller.isActive(playerId));

		clock.addAndGet(1_000L);
		controller.tick(player, group);
		assertFalse(controller.isActive(playerId));
		assertEquals(CopState.PURSUING, stateOf(marksman));

		// a repeated SEARCHING does not re-post
		states.get(marksman).set(CopState.PURSUING);
		controller.start(player, centre, 90, 4);
		assertFalse(controller.isActive(playerId));

		// SEEN clears the spent mark
		controller.end(player, CopState.PURSUING);
		controller.start(player, centre, 90, 4);
		assertTrue(controller.isActive(playerId));
	}

	@Test
	@DisplayName("Enabled false: nothing is posted")
	void disabled_doesNothing() {
		settings = new PerimeterSettings(false, 3, 2, List.of("Marksman"), 60, 16.0, 40.0, 4.0);
		chaser("Commander", 1);
		CopNpc marksman = chaser("Marksman", 2);

		controller.start(player, centre, 90, 5);

		verify(marksman, never()).holdPost(any());
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("a second start while active posts nobody twice")
	void secondStart_isIgnoredWhileActive() {
		chaser("Commander", 1);
		CopNpc marksman = chaser("Marksman", 2);
		controller.start(player, centre, 90, 4);

		controller.start(player, centre, 90, 4);

		verify(marksman, times(1)).holdPost(any());
	}

	@Test
	@DisplayName("a player with no cop group gets no perimeter")
	void noGroup_doesNothing() {
		when(manager.groupOf(playerId)).thenReturn(null);

		controller.start(player, centre, 90, 4);

		assertFalse(controller.isActive(playerId));
		verify(radio, never()).sayAs(any(), any(), any(), any());
		// tick on an inactive player is a no-op too
		controller.tick(player, null);
		verify(squad, never()).reportSighting(any());
	}
}
