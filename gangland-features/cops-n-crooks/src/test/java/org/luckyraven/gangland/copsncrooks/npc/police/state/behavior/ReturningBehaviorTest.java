package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.StuckSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11, D1: a cop rotated out of a pursuit used to re-engage on its first RETURNING tick because its target was
 * still online and free, bouncing PURSUING <-> RETURNING forever. Only a cop that started returning because its
 * target was restrained or jailed may re-engage, when that target is released (the admin-release case).
 */
@DisplayName("ReturningBehavior - re-engage only after a restrained target is released")
class ReturningBehaviorTest {

	private BukkitStatics     bukkit;
	private DetainmentService detainmentService;
	private CopNpc            cop;
	private Player            player;
	private ReturningBehavior behavior;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();

		UUID playerId = UUID.randomUUID();
		player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		bukkit.statics().when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

		cop = mock(CopNpc.class);
		when(cop.getTargetPlayerId()).thenReturn(playerId);

		detainmentService = mock(DetainmentService.class);
		behavior          = new ReturningBehavior(mock(CopSpawnManager.class), detainmentService, 600, 3.0,
		                                     StuckSettings.DEFAULT);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a cop rotated out while its target is free keeps returning instead of bouncing back (D1)")
	void rotatedOut_staysReturning() {
		behavior.onEnter(cop);
		behavior.tick(cop);
		behavior.tick(cop);

		verify(cop, never()).transitionTo(any());
	}

	@Test
	@DisplayName("a cop sent back by a restrained target re-engages once the target is released")
	void restrainedTargetReleased_reengages() {
		when(detainmentService.isRestrained(player)).thenReturn(true);
		behavior.onEnter(cop);
		when(detainmentService.isRestrained(player)).thenReturn(false);

		behavior.tick(cop);

		verify(cop).transitionTo(CopState.PURSUING);
	}

	@Test
	@DisplayName("returning leaves the squad, so the cop no longer holds a slot or a route plan")
	void onEnter_leavesSquad() {
		behavior.onEnter(cop);

		verify(cop).leaveSquad();
	}

	/** Wires a cop standing 100 blocks from a lone station so the behaviour is walking, not arriving. */
	private void farFromStation() {
		World world = mock(World.class);
		Location station = new Location(world, 100, 64, 0);
		CopSpawnManager spawns = mock(CopSpawnManager.class);
		when(spawns.getSpawnerLocations()).thenReturn(java.util.List.of(station));
		LivingEntity body = mock(LivingEntity.class);
		when(body.getWorld()).thenReturn(world);
		when(body.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		when(cop.getEntity()).thenReturn(body);
		behavior = new ReturningBehavior(spawns, detainmentService, 600, 3.0, StuckSettings.DEFAULT);
	}

	private long now;

	private void withClock(StuckSettings settings) {
		CopSpawnManager spawns = mock(CopSpawnManager.class);
		World world = mock(World.class);
		when(spawns.getSpawnerLocations()).thenReturn(java.util.List.of(new Location(world, 100, 64, 0)));
		LivingEntity body = mock(LivingEntity.class);
		when(body.getWorld()).thenReturn(world);
		when(body.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		when(cop.getEntity()).thenReturn(body);
		now      = 1_000_000L;
		behavior = new ReturningBehavior(spawns, detainmentService, 600, 3.0, settings, () -> now);
	}

	@Test
	@DisplayName("a return unreachable for the stuck window despawns the stranded cop")
	void unreachableLongerThanWindow_removed() {
		withClock(StuckSettings.DEFAULT);
		behavior.onEnter(cop);
		when(cop.millisUnreachable()).thenReturn(1_000L);
		behavior.tick(cop);
		verify(cop, never()).markForRemoval();

		now += StuckSettings.DEFAULT.recycleSeconds() * 1000L;
		behavior.tick(cop);

		verify(cop).markForRemoval();
	}

	@Test
	@DisplayName("a return unreachable for less than the window keeps walking")
	void unreachableShorterThanWindow_keepsWalking() {
		withClock(StuckSettings.DEFAULT);
		behavior.onEnter(cop);
		when(cop.millisUnreachable()).thenReturn(1_000L);
		behavior.tick(cop);
		now += 5_000L;
		behavior.tick(cop);

		verify(cop, never()).markForRemoval();
		verify(cop, times(2)).navigateTo(any());
	}

	@Test
	@DisplayName("a reachable route is not removed before arrival")
	void reachable_notRemoved() {
		withClock(StuckSettings.DEFAULT);
		behavior.onEnter(cop);
		when(cop.millisUnreachable()).thenReturn(0L);
		behavior.tick(cop);
		now += 60_000L;
		behavior.tick(cop);

		verify(cop, never()).markForRemoval();
	}

	@Test
	@DisplayName("a set-aside clock (reads 0 at entry) that later reads its old pre-entry value does not remove at once")
	void asideClockResurfaces_notRemovedAtOnce() {
		withClock(StuckSettings.DEFAULT);
		when(cop.millisUnreachable()).thenReturn(0L);
		behavior.onEnter(cop);
		now += 3_000L;
		when(cop.millisUnreachable()).thenReturn(40_000L);
		behavior.tick(cop);
		behavior.tick(cop);

		verify(cop, never()).markForRemoval();
	}

	@Test
	@DisplayName("a clock that reads reachable again restarts the stranded measurement")
	void resetThenRestart_countsFromRestart() {
		withClock(StuckSettings.DEFAULT);
		behavior.onEnter(cop);
		when(cop.millisUnreachable()).thenReturn(1_000L);
		behavior.tick(cop);
		now += 10_000L;
		when(cop.millisUnreachable()).thenReturn(0L);
		behavior.tick(cop);
		now += 10_000L;
		when(cop.millisUnreachable()).thenReturn(1_000L);
		behavior.tick(cop);
		now += 5_000L;
		behavior.tick(cop);

		verify(cop, never()).markForRemoval();
	}

	@Test
	@DisplayName("with the stuck recycler disabled a stranded return only despawns on the backstop")
	void stuckDisabled_notRemoved() {
		withClock(new StuckSettings(false, 12, 60));
		behavior.onEnter(cop);
		when(cop.millisUnreachable()).thenReturn(1_000L);
		behavior.tick(cop);
		now += 600_000L;
		behavior.tick(cop);

		verify(cop, never()).markForRemoval();
	}

	@Test
	@DisplayName("a pursuit-phase unreachable clock already over the window at entry does not remove a cop with a route home")
	void pursuitClockOverWindowAtEntry_notRemoved() {
		withClock(StuckSettings.DEFAULT);
		when(cop.millisUnreachable()).thenReturn(20_000L);
		behavior.onEnter(cop);

		behavior.tick(cop);
		behavior.tick(cop);

		verify(cop, never()).markForRemoval();
	}
}
