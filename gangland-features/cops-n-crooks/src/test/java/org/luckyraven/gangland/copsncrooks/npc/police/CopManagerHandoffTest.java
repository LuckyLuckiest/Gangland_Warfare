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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
