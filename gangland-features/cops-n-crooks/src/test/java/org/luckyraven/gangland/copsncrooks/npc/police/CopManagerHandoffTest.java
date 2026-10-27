package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BreatherSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DispatchSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.HandoffSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.PendingUnit;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
import org.luckyraven.gangland.copsncrooks.npc.police.handoff.HandoffController;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The hand-off end to end (CONTRACTS C11 into C8): a {@link HandoffController} on the {@link CopManagerFixture}
 * sets the bias when the chase leashes out, and the next units the manager enqueues carry it and seed the squad with
 * the hand-off position. Lives in this package for the package-private fixture.
 */
@DisplayName("CopManager - hand-off bias reaches the dispatcher and the spawned unit")
class CopManagerHandoffTest {

	private CopManagerFixture fx;
	private Player            player;
	private Wanted            wanted;

	@BeforeEach
	void setUp() {
		fx = new CopManagerFixture();
		when(fx.provider.getDispatchSettings()).thenReturn(DispatchSettings.DEFAULT);
		when(fx.provider.getBreatherSettings()).thenReturn(BreatherSettings.DEFAULT);
		when(fx.radio.compassWord(any(), any())).thenReturn("east");
		player = fx.player(10, 10);
		wanted = CopManagerFixture.wanted(2);
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	private void playerAt(double x) {
		when(player.getLocation()).thenReturn(new Location(fx.world, x, 64, 10));
	}

	@Test
	@DisplayName("a hand-off bias set by the controller is stamped on the next enqueued unit and seeds the spawned cop")
	void handoffBias_reachesTheDispatcherAndTheSpawnedUnit() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group = fx.manager.groupFor(player.getUniqueId());
		assertEquals(2, group.getCops().size());

		// the chase leashes out: seen running east, then both cops walk home from 500 blocks away
		playerAt(10);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);
		fx.clock[0] += 1_000L;
		playerAt(20);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);
		for (CopNpc cop : group.getCops()) {
			cop.transitionTo(CopState.RETURNING);
			when(cop.getEntity().getLocation()).thenReturn(new Location(fx.world, 500, 64, 10));
		}
		fx.clock[0] += 1_000L;
		playerAt(30);
		controller.tick(player, group);

		SpawnBias bias = group.biasAt(fx.clock[0]);
		assertNotNull(bias);
		verify(fx.radio).sayFromLeader(group, "Handoff", Map.of("direction", "east"), player);

		// he keeps running; the replacements are enqueued under the bias and seeded Lead_Blocks (24) on along the
		// heading the squad saw him take from where it last saw him (R5), not at his live x=90
		playerAt(90);
		fx.manager.spawnTick(player.getUniqueId(), wanted);

		ArgumentCaptor<PendingUnit> units = ArgumentCaptor.forClass(PendingUnit.class);
		verify(fx.spawner, atLeastOnce()).spawnUnit(eq(player), units.capture(), any());
		List<PendingUnit> carrying = units.getAllValues().stream().filter(u -> u.bias() != null).toList();
		assertTrue(carrying.size() >= 2, "both replacements carry the bias");
		for (PendingUnit unit : carrying) assertSame(bias, unit.bias());
		assertEquals(44.0, group.getSquad().lastKnownLocation().getX());
	}

	/**
	 * Seen running east (x=10, x=20) with both cops on him; then the second one leashes out and walks home 480 blocks
	 * off, still on his trail, while the first chases on by him (x=20). Returns {chaser, walker}.
	 */
	private CopNpc[] chaseWithALeashedWalker(HandoffController controller, CopGroup group) {
		playerAt(10);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);
		fx.clock[0] += 1_000L;
		playerAt(20);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);

		CopNpc chaser = group.getCops().get(0);
		CopNpc walker = group.getCops().get(1);
		when(chaser.getEntity().getLocation()).thenReturn(new Location(fx.world, 20, 64, 10));
		walker.transitionTo(CopState.RETURNING);
		when(walker.getEntity().getLocation()).thenReturn(new Location(fx.world, 500, 64, 10));
		fx.clock[0] += 1_000L;
		controller.tick(player, group);
		return new CopNpc[]{chaser, walker};
	}

	/** The cops lose him and post by the spot; he runs on to x=120 and leaves every post behind. */
	private void postsFallAway(HandoffController controller, CopGroup group) {
		for (CopNpc cop : group.getCops())
			if (cop.getCurrentState() == CopState.PURSUING) cop.transitionTo(CopState.POSTED);
		fx.clock[0] += 1_000L;
		controller.tick(player, group);
		playerAt(120);
		fx.clock[0] += 1_000L;
		controller.tick(player, group);
	}

	@Test
	@DisplayName("judge r5 Q1: the walker released (R3) while another cop still chases, then the posts fall away: hand-off")
	void walkerReleasedDuringTheChase_thenPostsFallAway_handsOff() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group  = fx.manager.groupFor(player.getUniqueId());
		CopNpc   walker = chaseWithALeashedWalker(controller, group)[1];

		when(fx.spawner.spawnUnit(any(), any(), any())).thenReturn(null);
		fx.manager.spawnTick(player.getUniqueId(), wanted); // the walker leaves the map while the chaser chases
		assertFalse(group.getCops().contains(walker), "the leashed walker was released");
		fx.clock[0] += 1_000L;
		controller.tick(player, group); // the chaser still chases him
		postsFallAway(controller, group);

		assertNotNull(group.biasAt(fx.clock[0]), "the chase leashed out: the hand-off fires");
	}

	@Test
	@DisplayName("judge r5 Q1c control: the leashed walker still walking home when the posts fall away hands off")
	void walkerStillThere_thenPostsFallAway_handsOff() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group = fx.manager.groupFor(player.getUniqueId());
		chaseWithALeashedWalker(controller, group);
		fx.clock[0] += 1_000L;
		controller.tick(player, group);
		postsFallAway(controller, group);

		assertNotNull(group.biasAt(fx.clock[0]));
	}

	@Test
	@DisplayName("0.21.1: a refill chasing from the release tick on does not wipe the leash: when it loses him, hand-off")
	void walkerReleased_refillChasesAtOnce_thenLosesHim_handsOff() {
		// dispatch off: the refill spawns in the very run that releases the walker
		when(fx.provider.getDispatchSettings()).thenReturn(DispatchSettings.DISABLED);
		when(fx.provider.getBreatherSettings()).thenReturn(BreatherSettings.DISABLED);
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group  = fx.manager.groupFor(player.getUniqueId());
		CopNpc   walker = chaseWithALeashedWalker(controller, group)[1];

		fx.manager.spawnTick(player.getUniqueId(), wanted);
		assertFalse(group.getCops().contains(walker), "the leashed walker was released");
		assertEquals(2, group.getCops().stream().filter(c -> c.getCurrentState() == CopState.PURSUING).count(),
		             "the chaser and its refill chase him");
		fx.clock[0] += 1_000L;
		controller.tick(player, group);
		postsFallAway(controller, group);

		assertNotNull(group.biasAt(fx.clock[0]), "the leash still stands when the chase falls away");
	}

	@Test
	@DisplayName("0.21.1: a leashed walker its walk home took off the map (marked for removal) still hands the chase off")
	void leashedWalkerMarkedForRemoval_released_stillHandsOff() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group  = fx.manager.groupFor(player.getUniqueId());
		CopNpc   walker = chaseWithALeashedWalker(controller, group)[1];
		// its walk home ended it (stranded, Max_Ticks or a station) before any R3 release
		when(walker.isMarkedForRemoval()).thenReturn(true);

		when(fx.spawner.spawnUnit(any(), any(), any())).thenReturn(null);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		assertFalse(group.getCops().contains(walker), "the marked walker was released");
		fx.clock[0] += 1_000L;
		controller.tick(player, group);
		postsFallAway(controller, group);

		assertNotNull(group.biasAt(fx.clock[0]), "the chase leashed out: the hand-off fires");
	}

	@Test
	@DisplayName("0.21.1: a leash release recorded in one episode never hands off the next: a wanted start or end clears it")
	void leashRelease_clearedByWantedStartAndEnd() {
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group = fx.manager.groupFor(player.getUniqueId());

		group.markLeashReleased(fx.clock[0]);
		fx.manager.onWantedEnd(player, org.luckyraven.gangland.core.wanted.WantedCause.ADMIN);
		assertFalse(group.isLeashReleased(fx.clock[0], 10_000L), "a wanted end clears it");

		group.markLeashReleased(fx.clock[0]);
		fx.manager.onWantedStart(player, wanted);
		assertFalse(group.isLeashReleased(fx.clock[0], 10_000L), "a wanted start clears it");
	}

	@Test
	@DisplayName("0.21.1 review 2: a marked leashed walker the AI tick reaps (before any spawn run) still hands off")
	void markedLeashedWalker_reapedByTheAiTick_thenPostsFallAway_handsOff() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group  = fx.manager.groupFor(player.getUniqueId());
		CopNpc   walker = chaseWithALeashedWalker(controller, group)[1];
		// ReturningBehavior marked it inside an AI tick; the next AI tick (every 10 game ticks) reaps it, before the
		// spawn task (every 40) sees it
		when(walker.isMarkedForRemoval()).thenReturn(true);

		fx.manager.aiTick(player.getUniqueId());
		assertFalse(group.getCops().contains(walker), "the AI tick reaped the marked walker");
		fx.clock[0] += 1_000L;
		controller.tick(player, group);
		postsFallAway(controller, group);

		assertNotNull(group.biasAt(fx.clock[0]), "the chase leashed out: the hand-off fires");
	}

	@Test
	@DisplayName("0.21.1 review 2: a walker leashed out early, then the chaser cuffs him: a cuff is no lost chase")
	void staleRelease_thenTheChaserCuffs_noHandoff() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group  = fx.manager.groupFor(player.getUniqueId());
		CopNpc[] cops   = chaseWithALeashedWalker(controller, group);
		CopNpc   chaser = cops[0];

		when(fx.spawner.spawnUnit(any(), any(), any())).thenReturn(null);
		fx.manager.spawnTick(player.getUniqueId(), wanted); // the leashed walker leaves the map
		assertFalse(group.getCops().contains(cops[1]));
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		chaser.transitionTo(CopState.CUFFING);
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		assertNull(group.biasAt(fx.clock[0]), "a cuff is no lost chase");
		verify(fx.radio, never()).sayFromLeader(eq(group), eq("Handoff"), any(), any());
	}

	@Test
	@DisplayName("0.21.1 review 2: the cuff landed (GUARDING), the rest sent home beside him: no hand-off")
	void staleRelease_thenCuffedAndGuarded_noHandoff() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group  = fx.manager.groupFor(player.getUniqueId());
		CopNpc[] cops   = chaseWithALeashedWalker(controller, group);
		CopNpc   chaser = cops[0];

		fx.manager.spawnTick(player.getUniqueId(), wanted); // the walker leaves, its refill arrives and chases
		assertFalse(group.getCops().contains(cops[1]));
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		chaser.transitionTo(CopState.GUARDING);
		for (CopNpc cop : group.getCops()) {
			if (cop == chaser) continue;
			cop.transitionTo(CopState.RETURNING); // sent back: target restrained, beside him, no leash
			when(cop.getEntity().getLocation()).thenReturn(new Location(fx.world, 25, 64, 10));
		}
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		assertNull(group.biasAt(fx.clock[0]), "a cuffed suspect is no lost chase");
		verify(fx.radio, never()).sayFromLeader(eq(group), eq("Handoff"), any(), any());
	}

	@Test
	@DisplayName("0.21.1 review 2: a leash release a minute old has expired (Bias_Seconds): a squad wiped later lost no chase")
	void staleRelease_thenTheSquadIsWipedAMinuteLater_noHandoff() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group  = fx.manager.groupFor(player.getUniqueId());
		CopNpc   chaser = chaseWithALeashedWalker(controller, group)[0];

		when(fx.spawner.spawnUnit(any(), any(), any())).thenReturn(null);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		for (int i = 0; i < 60; i++) { // a minute of chase
			fx.clock[0] += 1_000L;
			controller.tick(player, group);
		}
		when(chaser.isValid()).thenReturn(false); // killed
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		assertNull(group.biasAt(fx.clock[0]), "a wiped squad lost no chase");
	}

	@Test
	@DisplayName("0.21.1 r6: a post alone holds the chase past Bias_Seconds after the release, then falls away: hand-off")
	void pa_postHoldsPastBiasSeconds_thenFallsAway_handsOff() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group  = fx.manager.groupFor(player.getUniqueId());
		CopNpc[] cops   = chaseWithALeashedWalker(controller, group);
		CopNpc   chaser = cops[0];

		when(fx.spawner.spawnUnit(any(), any(), any())).thenReturn(null);
		fx.manager.spawnTick(player.getUniqueId(), wanted); // R3 takes the leashed walker off the map
		assertFalse(group.getCops().contains(cops[1]), "the leashed walker was released");
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		// the chaser loses him and posts at x=20; he hides 40 blocks from the post for 15 s
		chaser.transitionTo(CopState.POSTED);
		playerAt(60);
		for (int i = 0; i < 15; i++) {
			fx.clock[0] += 1_000L;
			controller.tick(player, group);
		}
		assertNull(group.biasAt(fx.clock[0]), "the post still holds the chase");

		playerAt(120); // he runs on past Max_Distance (80) of the post
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		assertNotNull(group.biasAt(fx.clock[0]), "the chase leashed out: the hand-off fires");
	}

	@Test
	@DisplayName("0.21.1 r6 review 1: a post holds a minute after the release, then dies: no hand-off (nobody leashed out)")
	void postHoldsAMinute_thenThePostIsKilled_noHandoff() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group  = fx.manager.groupFor(player.getUniqueId());
		CopNpc[] cops   = chaseWithALeashedWalker(controller, group);
		CopNpc   chaser = cops[0];

		when(fx.spawner.spawnUnit(any(), any(), any())).thenReturn(null);
		fx.manager.spawnTick(player.getUniqueId(), wanted); // R3 takes the leashed walker off the map
		assertFalse(group.getCops().contains(cops[1]), "the leashed walker was released");
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		// the chaser posts at x=20; he hides 40 blocks from the post for a minute
		chaser.transitionTo(CopState.POSTED);
		playerAt(60);
		for (int i = 0; i < 60; i++) {
			fx.clock[0] += 1_000L;
			controller.tick(player, group);
		}
		when(chaser.isValid()).thenReturn(false); // the post is killed
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		assertNull(group.biasAt(fx.clock[0]), "a post killed a minute after the release lost no chase");
		verify(fx.radio, never()).sayFromLeader(eq(group), eq("Handoff"), any(), any());
	}

	@Test
	@DisplayName("0.21.1: the leashed walker released out of view (R3) before the posts fall away still hands the chase off")
	void leashedWalkerReleasedBeforeTheEdge_stillHandsOff() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group = fx.manager.groupFor(player.getUniqueId());
		playerAt(10);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);
		fx.clock[0] += 1_000L;
		playerAt(20);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);

		// one cop holds a post by him, the other leashed out and walks home 480 blocks off, still on his trail
		CopNpc posted = group.getCops().get(0);
		CopNpc walker = group.getCops().get(1);
		posted.transitionTo(CopState.POSTED);
		when(posted.getEntity().getLocation()).thenReturn(new Location(fx.world, 20, 64, 10));
		walker.transitionTo(CopState.RETURNING);
		when(walker.getEntity().getLocation()).thenReturn(new Location(fx.world, 500, 64, 10));
		fx.clock[0] += 1_000L;
		controller.tick(player, group);
		assertNull(group.biasAt(fx.clock[0]), "the post still holds the chase");

		// the spawn run takes the unwatched walker off the map (R3), its refill still en route; then he runs on and
		// leaves the post behind
		when(fx.spawner.spawnUnit(any(), any(), any())).thenReturn(null);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		assertFalse(group.getCops().contains(walker), "the leashed walker was released");
		playerAt(120);
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		assertNotNull(group.biasAt(fx.clock[0]), "the chase leashed out: the hand-off fires");
		verify(fx.radio).sayFromLeader(group, "Handoff", Map.of("direction", "east"), player);
	}

	@Test
	@DisplayName("T-432: the post-escape search runs at one star but he has none: a leash there hands nothing off")
	void postEscapeSearch_leashOut_noHandoff() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.setEscapePredicate(cause -> cause == org.luckyraven.gangland.core.wanted.WantedCause.EVASION);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group = fx.manager.groupFor(player.getUniqueId());
		fx.manager.onWantedEnd(player, org.luckyraven.gangland.core.wanted.WantedCause.EVASION);
		when(fx.targeting.isSearching(player.getUniqueId())).thenReturn(true);
		fx.manager.spawnTick(player.getUniqueId(), CopManagerFixture.wanted(0));
		assertEquals(1, group.getLevel(), "the search's squad runs at one star (T-187)");

		playerAt(10);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);
		fx.clock[0] += 1_000L;
		playerAt(20);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);
		for (CopNpc cop : group.getCops()) {
			cop.transitionTo(CopState.RETURNING);
			when(cop.getEntity().getLocation()).thenReturn(new Location(fx.world, 500, 64, 10));
		}
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		assertNull(group.biasAt(fx.clock[0]));
		verify(fx.radio, org.mockito.Mockito.never()).sayFromLeader(eq(group), eq("Handoff"), any(), any());
	}

	/** Seen running east (x=10, x=20), then the chase leashes out with him unseen: the hand-off at x=20, heading east. */
	private CopGroup handOffAfterARunEast(HandoffController controller) {
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group = fx.manager.groupFor(player.getUniqueId());
		playerAt(10);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);
		fx.clock[0] += 1_000L;
		playerAt(20);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);
		for (CopNpc cop : group.getCops()) {
			cop.transitionTo(CopState.RETURNING);
			when(cop.getEntity().getLocation()).thenReturn(new Location(fx.world, 500, 64, 10));
		}
		fx.clock[0] += 1_000L;
		controller.tick(player, group);
		assertNotNull(group.biasAt(fx.clock[0]));
		return group;
	}

	@Test
	@DisplayName("R5: the hand-off units head where the squad saw him going, Lead_Blocks on; his live spot never leaks")
	void handoffSeed_isTheLastKnownSpotLedAlongTheSquadSeenHeading() {
		CopGroup group = handOffAfterARunEast(new HandoffController(fx.manager, fx.radio, () -> fx.provider));

		// unseen, he doubled back north-west
		when(player.getLocation()).thenReturn(new Location(fx.world, -40, 64, -60));
		fx.manager.spawnTick(player.getUniqueId(), wanted);

		Location goal = group.getSquad().lastKnownLocation();
		assertEquals(20.0 + HandoffSettings.DEFAULT.leadBlocks(), goal.getX(), 1e-9, "east of the last-known spot");
		assertEquals(10.0, goal.getZ(), 1e-9, "on the squad-seen heading, not toward his live spot");
	}

	@Test
	@DisplayName("R5: Lead_Blocks is clamped inside the spawn leash margin (0.75 x Pursuit.Max_Distance)")
	void handoffLead_clampedInsideTheLeashMargin() {
		when(fx.provider.getHandoffSettings()).thenReturn(new HandoffSettings(true, 2, 10, 60.0, 500.0));
		CopGroup group = handOffAfterARunEast(new HandoffController(fx.manager, fx.radio, () -> fx.provider));

		fx.manager.spawnTick(player.getUniqueId(), wanted);

		assertEquals(20.0 + 0.75 * 80.0, group.getSquad().lastKnownLocation().getX(), 1e-9);
	}

	@Test
	@DisplayName("R5: a second hand-off with no news since the first leads from the squad's last-known spot again, not from the first lead")
	void secondHandoffWithoutNews_leadsFromTheLastKnownSpotAgain() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		CopGroup          group      = handOffAfterARunEast(controller);
		// unseen, he doubled back; the hand-off units come and head 24 on, east
		when(player.getLocation()).thenReturn(new Location(fx.world, -40, 64, -60));
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		controller.tick(player, group);
		assertEquals(44.0, group.getSquad().lastKnownLocation().getX(), 1e-9);

		// they find nobody there and leash out; the first bias ran out, the hand-off fires again
		fx.clock[0] += 11_000L;
		for (CopNpc cop : group.getCops()) {
			cop.transitionTo(CopState.RETURNING);
			when(cop.getEntity().getLocation()).thenReturn(new Location(fx.world, 500, 64, 10));
		}
		controller.tick(player, group);
		SpawnBias second = group.biasAt(fx.clock[0]);
		assertNotNull(second);
		assertEquals(20.0, second.lastSeen().getX(), 1e-9, "the squad's last-known spot, not the first lead");
		fx.manager.spawnTick(player.getUniqueId(), wanted);

		assertEquals(44.0, group.getSquad().lastKnownLocation().getX(), 1e-9, "never 68: the lead does not stack up");
	}

	@Test
	@DisplayName("T-434: a biased unit spawning after a fresher sighting keeps that sighting, and the next hand-off heads on")
	void handoffSeed_neverRewindsAFresherSighting() {
		HandoffController controller = new HandoffController(fx.manager, fx.radio, () -> fx.provider);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		CopGroup group = fx.manager.groupFor(player.getUniqueId());

		// seen at x=10 then x=20, then the chase leashes out: hand-off at x=20, heading east
		playerAt(10);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);
		fx.clock[0] += 1_000L;
		playerAt(20);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);
		for (CopNpc cop : group.getCops()) {
			cop.transitionTo(CopState.RETURNING);
			when(cop.getEntity().getLocation()).thenReturn(new Location(fx.world, 500, 64, 10));
		}
		fx.clock[0] += 1_000L;
		controller.tick(player, group);
		assertNotNull(group.biasAt(fx.clock[0]));

		// a cop sees him at x=50 after the hand-off; then the biased refill spawns
		fx.clock[0] += 1_000L;
		playerAt(50);
		group.getSquad().reportSighting(player.getLocation());
		controller.tick(player, group);
		fx.manager.spawnTick(player.getUniqueId(), wanted);
		assertEquals(50.0, group.getSquad().lastKnownLocation().getX(), "the stale hand-off spot never rewinds a sighting");

		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		// the bias runs out; the refill chases him, then leashes out again: the second hand-off
		fx.clock[0] += 10_000L;
		controller.tick(player, group);
		for (CopNpc cop : group.getCops()) {
			cop.transitionTo(CopState.RETURNING);
			when(cop.getEntity().getLocation()).thenReturn(new Location(fx.world, 500, 64, 10));
		}
		fx.clock[0] += 1_000L;
		controller.tick(player, group);

		SpawnBias second = group.biasAt(fx.clock[0]);
		assertNotNull(second, "the second hand-off");
		assertTrue(second.heading().getX() > 0, "he ran east (20 then 50): heading " + second.heading());
		assertEquals(50.0, second.lastSeen().getX());
	}
}
