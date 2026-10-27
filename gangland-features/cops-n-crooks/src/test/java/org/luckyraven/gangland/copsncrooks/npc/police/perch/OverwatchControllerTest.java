package org.luckyraven.gangland.copsncrooks.npc.police.perch;

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
import org.luckyraven.gangland.copsncrooks.npc.police.config.OverwatchSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcPost;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OverwatchController} (P-OVW-1, P-OVW-2, P-OVW-3): who is sent where, how a missed perch is counted, what a
 * perched Marksman reports and the ways the overwatch ends. Cops are state-tracking mocks whose entity position can be
 * moved; the perch finder is a fixed list and the clock is manual.
 */
@DisplayName("OverwatchController")
class OverwatchControllerTest {

	private final UUID              playerId = UUID.randomUUID();
	private final Player            player   = mock(Player.class);
	private final World             world    = mock(World.class);
	private final Location          lastSeen = new Location(world, 0, 64, 0);
	private final CopManager        manager  = mock(CopManager.class);
	private final CopRadio          radio    = mock(CopRadio.class);
	private final CopGroup          group    = mock(CopGroup.class);
	private final NpcSquad          squad    = mock(NpcSquad.class);
	private final List<CopNpc>      cops     = new ArrayList<>();
	private final AtomicLong        clock    = new AtomicLong(1_000_000L);
	private final AtomicInteger     level    = new AtomicInteger(4);
	private final CopConfigProvider provider = mock(CopConfigProvider.class);

	private final List<Location> perches = List.of(new Location(world, 10, 71, 0), new Location(world, 0, 71, 12),
	                                               new Location(world, -14, 71, -14));

	private OverwatchSettings settings = OverwatchSettings.DEFAULT;
	private List<Location>    found    = perches;

	private final Map<CopNpc, AtomicReference<CopState>> states    = new java.util.HashMap<>();
	private final Map<CopNpc, AtomicReference<Location>> positions = new java.util.HashMap<>();

	private final OverwatchController controller;

	OverwatchControllerTest() {
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("Runner");
		when(player.getLocation()).thenReturn(new Location(world, 30, 71, 0));
		when(manager.groupOf(playerId)).thenReturn(group);
		when(group.getCops()).thenReturn(cops);
		when(group.getSquad()).thenReturn(squad);
		when(provider.getOverwatchSettings()).thenAnswer(i -> settings);
		when(radio.compassWord(any(), any())).thenReturn("north");
		controller = new OverwatchController(() -> provider, manager, radio, clock::get, p -> level.get(),
		                                     (spot, s) -> found);
	}

	private CopNpc cop(String role, CopState state, UUID target) {
		CopNpc cop = mock(CopNpc.class);
		AtomicReference<CopState> current = new AtomicReference<>(state);
		AtomicReference<Location> at      = new AtomicReference<>(new Location(world, 40, 64, 40));
		states.put(cop, current);
		positions.put(cop, at);
		CopRole copRole = new CopRole(role, role, NpcFanPlacement.ANY, null, null, 1.0, null, 0, 0.0, 1.0, 0, null, 0.0,
		                              0.0, false, false);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenAnswer(i -> at.get());
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

	private CopNpc marksman() {
		return cop("Marksman", CopState.PURSUING, playerId);
	}

	private CopState stateOf(CopNpc cop) {
		return states.get(cop).get();
	}

	private void moveTo(CopNpc cop, Location spot) {
		positions.get(cop).set(spot.clone());
	}

	private void advance(long seconds) {
		clock.addAndGet(seconds * 1000L);
	}

	/** Start, walk to the first perch and tick once so the post is taken. */
	private CopNpc perched() {
		CopNpc marksman = marksman();
		controller.start(player, lastSeen, 4);
		moveTo(marksman, perches.get(0));
		controller.tick(player, group);
		return marksman;
	}

	@Test
	@DisplayName("four stars, contact lost, Marksman pursuing: he goes to OVERWATCH and walks to the nearest perch")
	void fourStarsContactLostSendsMarksman() {
		CopNpc marksman = marksman();
		CopNpc assault  = cop("Assault", CopState.PURSUING, playerId);

		controller.start(player, lastSeen, 4);

		assertEquals(CopState.OVERWATCH, stateOf(marksman));
		verify(marksman).navigateTo(perches.get(0));
		assertTrue(controller.isActive(playerId));
		verify(assault, never()).navigateTo(any());
		assertEquals(CopState.PURSUING, stateOf(assault));
	}

	@Test
	@DisplayName("the walk starts after the state change (the chase state's exit stops navigation)")
	void walkStartsAfterTheTransition() {
		CopNpc marksman = marksman();

		controller.start(player, lastSeen, 4);

		org.mockito.InOrder order = org.mockito.Mockito.inOrder(marksman);
		order.verify(marksman).transitionTo(CopState.OVERWATCH);
		order.verify(marksman).navigateTo(any());
	}

	@Test
	@DisplayName("an idle Marksman of the squad is eligible too")
	void idleMarksmanIsEligible() {
		CopNpc marksman = cop("Marksman", CopState.IDLE, null);

		controller.start(player, lastSeen, 4);

		assertEquals(CopState.OVERWATCH, stateOf(marksman));
	}

	@Test
	@DisplayName("three stars: nothing happens")
	void threeStarsNoOverwatch() {
		CopNpc marksman = marksman();

		controller.start(player, lastSeen, 3);

		assertEquals(CopState.PURSUING, stateOf(marksman));
		verify(marksman, never()).navigateTo(any());
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("Overwatch.Enable false: nothing happens")
	void disabledDoesNothing() {
		settings = new OverwatchSettings(false, 4, "Marksman", 10.0, 20.0, 8, 4, 48.0, 3, 20, 120);
		CopNpc marksman = marksman();

		controller.start(player, lastSeen, 5);

		assertEquals(CopState.PURSUING, stateOf(marksman));
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("Min_Level and Role come from the config, read at every call")
	void settingsAreReadFromConfig() {
		settings = new OverwatchSettings(true, 5, "Assault", 10.0, 20.0, 8, 4, 48.0, 3, 20, 120);
		CopNpc marksman = marksman();
		CopNpc assault  = cop("Assault", CopState.PURSUING, playerId);

		controller.start(player, lastSeen, 4);
		assertFalse(controller.isActive(playerId), "level 4 is under Min_Level 5");

		controller.start(player, lastSeen, 5);
		assertEquals(CopState.OVERWATCH, stateOf(assault));
		assertEquals(CopState.PURSUING, stateOf(marksman), "only the configured Role is sent");
	}

	@Test
	@DisplayName("no Marksman in the squad, a Marksman posted by the perimeter, or one chasing someone else: nothing")
	void noEligibleMarksman() {
		cop("Assault", CopState.PURSUING, playerId);
		CopNpc posted = cop("Marksman", CopState.POSTED, playerId);
		CopNpc other  = cop("Marksman", CopState.PURSUING, UUID.randomUUID());

		controller.start(player, lastSeen, 4);

		assertFalse(controller.isActive(playerId));
		assertEquals(CopState.POSTED, stateOf(posted));
		assertEquals(CopState.PURSUING, stateOf(other));
	}

	@Test
	@DisplayName("no perch overlooks the spot, or the player has no group: nothing")
	void noPerchesNoGroup() {
		CopNpc marksman = marksman();
		found = List.of();

		controller.start(player, lastSeen, 4);
		assertEquals(CopState.PURSUING, stateOf(marksman));
		assertFalse(controller.isActive(playerId));

		found = perches;
		when(manager.groupOf(playerId)).thenReturn(null);
		controller.start(player, lastSeen, 4);
		assertEquals(CopState.PURSUING, stateOf(marksman));
	}

	@Test
	@DisplayName("a second start while active does not send him again")
	void secondStartIsIgnored() {
		CopNpc marksman = marksman();

		controller.start(player, lastSeen, 4);
		controller.start(player, lastSeen, 4);

		verify(marksman, times(1)).navigateTo(any());
	}

	@Test
	@DisplayName("reaching the perch holds a post there, watching the last known spot")
	void reachingThePerchHoldsAPost() {
		CopNpc marksman = marksman();
		controller.start(player, lastSeen, 4);

		controller.tick(player, group);
		verify(marksman, never()).holdPost(any());

		moveTo(marksman, new Location(world, 10.5, 71, 0.8));
		controller.tick(player, group);

		ArgumentCaptor<NpcPost> post = ArgumentCaptor.forClass(NpcPost.class);
		verify(marksman).holdPost(post.capture());
		assertEquals(perches.get(0).getX(), post.getValue().anchor().getX(), 1.0E-6);
		assertEquals(perches.get(0).getY(), post.getValue().anchor().getY(), 1.0E-6);
		assertEquals(lastSeen.getX(), post.getValue().watch().getX(), 1.0E-6);
		assertEquals(lastSeen.getY(), post.getValue().watch().getY(), 1.0E-6);
		assertEquals(lastSeen.getZ(), post.getValue().watch().getZ(), 1.0E-6);
		assertEquals(CopState.OVERWATCH, stateOf(marksman));
	}

	@Test
	@DisplayName("a perch not reached in Reach_Seconds counts a failure and sends him to the next one")
	void unreachedPerchTriesTheNext() {
		CopNpc marksman = marksman();
		controller.start(player, lastSeen, 4);

		advance(19);
		controller.tick(player, group);
		verify(marksman, never()).navigateTo(perches.get(1));

		advance(2);
		controller.tick(player, group);

		verify(marksman).navigateTo(perches.get(1));
		assertEquals(CopState.OVERWATCH, stateOf(marksman));
	}

	@Test
	@DisplayName("a perch the navigator reports unreachable moves on at once")
	void unreachablePerchMovesOnAtOnce() {
		CopNpc marksman = marksman();
		when(marksman.millisUnreachable()).thenReturn(OverwatchController.UNREACHABLE_MILLIS + 1);
		controller.start(player, lastSeen, 4);

		controller.tick(player, group);

		verify(marksman).navigateTo(perches.get(1));
	}

	@Test
	@DisplayName("3 perches never reached: he gives up after the third failure and goes back to the chase")
	void unreachedPerchCountsFailureAndGivesUpAtThree() {
		found = new java.util.ArrayList<>(perches);
		found.add(new Location(world, 14, 71, 14));
		CopNpc marksman = marksman();
		controller.start(player, lastSeen, 4);

		for (int i = 0; i < 3; i++) {
			assertTrue(controller.isActive(playerId), "still active before failure " + (i + 1));
			advance(21);
			controller.tick(player, group);
		}

		assertFalse(controller.isActive(playerId));
		assertEquals(CopState.PURSUING, stateOf(marksman));
		verify(marksman).navigateTo(perches.get(1));
		verify(marksman).navigateTo(perches.get(2));
		verify(marksman, never()).navigateTo(found.get(3));
		verify(marksman, never()).holdPost(any());
	}

	@Test
	@DisplayName("running out of perches before Max_Failures also gives up")
	void runningOutOfPerchesGivesUp() {
		found = List.of(perches.get(0));
		CopNpc marksman = marksman();
		controller.start(player, lastSeen, 4);

		advance(21);
		controller.tick(player, group);

		assertFalse(controller.isActive(playerId));
		assertEquals(CopState.PURSUING, stateOf(marksman));
	}

	@Test
	@DisplayName("a perched Marksman who sees the suspect at 40 blocks reports the sighting and radios it")
	void perchedMarksmanSightingReports() {
		CopNpc marksman = perched();
		when(marksman.canSee(player, 48.0)).thenReturn(true);
		when(player.getLocation()).thenReturn(new Location(world, 40, 71, 0));
		moveTo(marksman, new Location(world, 10, 71, 0));

		controller.tick(player, group);

		// a mark by the Marksman at its own sight range (Keystone 1.15.4): members chase only while he still sees
		verify(squad).reportSighting(marksman, player.getLocation(), 48.0);
		@SuppressWarnings({"unchecked", "rawtypes"})
		ArgumentCaptor<Map<String, String>> extra = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
		verify(radio).sayAs(eq(group), eq(marksman), eq("Overwatch_Spotted"), extra.capture());
		assertEquals("30", extra.getValue().get("distance"));
		assertEquals("north", extra.getValue().get("direction"));
		assertEquals(CopState.OVERWATCH, stateOf(marksman));
		assertTrue(controller.isActive(playerId));
	}

	@Test
	@DisplayName("a perched Marksman who cannot see the suspect reports nothing")
	void perchedMarksmanWithoutSightReportsNothing() {
		CopNpc marksman = perched();
		when(marksman.canSee(any(LivingEntity.class), anyDouble())).thenReturn(false);

		controller.tick(player, group);

		verify(squad, never()).reportSighting(any());
		verify(radio, never()).sayAs(any(), any(), eq("Overwatch_Spotted"), any());
	}

	@Test
	@DisplayName("a Marksman still walking to the perch does not report")
	void walkingMarksmanDoesNotReport() {
		CopNpc marksman = marksman();
		when(marksman.canSee(any(LivingEntity.class), anyDouble())).thenReturn(true);
		controller.start(player, lastSeen, 4);

		controller.tick(player, group);

		verify(squad, never()).reportSighting(any());
	}

	@Test
	@DisplayName("the overwatch ends at Max_Seconds: post released, back to the chase")
	void endsAfterMaxSeconds() {
		CopNpc marksman = perched();

		advance(119);
		controller.tick(player, group);
		assertTrue(controller.isActive(playerId), "119 s is still inside Max_Seconds");

		advance(2);
		controller.tick(player, group);

		assertFalse(controller.isActive(playerId));
		verify(marksman).releasePost();
		assertEquals(CopState.PURSUING, stateOf(marksman));
	}

	@Test
	@DisplayName("Max_Seconds is read from the config at every tick (reload)")
	void maxSecondsFollowsAReload() {
		CopNpc marksman = perched();
		settings = new OverwatchSettings(true, 4, "Marksman", 10.0, 20.0, 8, 4, 48.0, 3, 20, 10);

		advance(11);
		controller.tick(player, group);

		assertFalse(controller.isActive(playerId));
		assertEquals(CopState.PURSUING, stateOf(marksman));
	}

	@Test
	@DisplayName("a star drop below Min_Level brings him down")
	void endsOnStarDrop() {
		CopNpc marksman = perched();
		level.set(3);

		controller.tick(player, group);

		assertFalse(controller.isActive(playerId));
		verify(marksman).releasePost();
		assertEquals(CopState.PURSUING, stateOf(marksman));
	}

	@Test
	@DisplayName("the clock running out is not restarted by the next SEARCHING until end() clears the mark")
	void spentOverwatchIsNotRestarted() {
		CopNpc marksman = perched();
		advance(121);
		controller.tick(player, group);

		controller.start(player, lastSeen, 4);
		assertFalse(controller.isActive(playerId));
		assertEquals(CopState.PURSUING, stateOf(marksman));

		controller.end(player, CopState.RETURNING);
		controller.start(player, lastSeen, 4);
		assertTrue(controller.isActive(playerId));
	}

	@Test
	@DisplayName("end sends him to the asked state, releases the post and clears the overwatch")
	void endReleasesIntoTheAskedState() {
		CopNpc marksman = perched();

		controller.end(player, CopState.RETURNING);

		assertFalse(controller.isActive(playerId));
		verify(marksman).releasePost();
		assertEquals(CopState.RETURNING, stateOf(marksman));
	}

	@Test
	@DisplayName("end with no overwatch running is a no-op")
	void endWithoutOverwatchIsANoOp() {
		CopNpc marksman = marksman();

		controller.end(player, CopState.RETURNING);

		verify(marksman, never()).releasePost();
		assertEquals(CopState.PURSUING, stateOf(marksman));
	}

	@Test
	@DisplayName("a Marksman forced out of OVERWATCH (the suspect shot at him) is dropped without touching his new state")
	void forcedOutIsDropped() {
		CopNpc marksman = perched();
		marksman.transitionTo(CopState.COMBAT);

		controller.tick(player, group);

		assertFalse(controller.isActive(playerId));
		assertEquals(CopState.COMBAT, stateOf(marksman));
		verify(marksman, never()).transitionTo(CopState.PURSUING);
	}

	@Test
	@DisplayName("a dead Marksman ends the overwatch quietly")
	void deadMarksmanEndsIt() {
		CopNpc marksman = perched();
		when(marksman.isValid()).thenReturn(false);

		controller.tick(player, group);

		assertFalse(controller.isActive(playerId));
		verify(marksman, never()).transitionTo(CopState.PURSUING);
	}

	@Test
	@DisplayName("the player's group is gone (offline, squad dead): the Marksman is released home and the overwatch dropped")
	void groupGoneReleasesHome() {
		CopNpc marksman = perched();

		controller.tick(player, null);

		assertFalse(controller.isActive(playerId));
		verify(marksman).releasePost();
		assertEquals(CopState.RETURNING, stateOf(marksman));
	}

	@Test
	@DisplayName("tick for a player with no overwatch does nothing")
	void tickWithoutOverwatchDoesNothing() {
		CopNpc marksman = marksman();

		controller.tick(player, group);

		verify(marksman, never()).navigateTo(any());
		verify(marksman, never()).releasePost();
	}

	@Test
	@DisplayName("end() with a despawned Marksman (null entity) does not throw")
	void endWithDespawnedMarksmanDoesNotThrow() {
		CopNpc marksman = perched();
		when(marksman.getEntity()).thenReturn(null);
		when(marksman.isValid()).thenReturn(false);
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> controller.end(player, CopState.RETURNING));
		assertFalse(controller.isActive(playerId));
	}

	@Test
	@DisplayName("cnc-integ INT-1/5 (C-11): a group in a 0.21 standoff sends no Marksman to a perch")
	void integ_startSkipsAGroupInStandoff() {
		CopNpc marksman = marksman();
		when(group.isStandoff(anyLong())).thenReturn(true);

		controller.start(player, lastSeen, 4);

		assertEquals(CopState.PURSUING, stateOf(marksman));
		verify(marksman, never()).navigateTo(any());
	}

	@Test
	@DisplayName("cnc-integ INT-1/5 (C-11): without a standoff the same Marksman is still sent (sensitivity)")
	void integ_startWithoutStandoffStillSends() {
		CopNpc marksman = marksman();
		when(group.isStandoff(anyLong())).thenReturn(false);

		controller.start(player, lastSeen, 4);

		assertEquals(CopState.OVERWATCH, stateOf(marksman));
	}
}
