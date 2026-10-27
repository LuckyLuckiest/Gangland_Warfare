package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PatrolSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 0.20.0 lane patrol (P-PAT-1, PAT-HIT, PAT-BEAT): beat cops spawn at a station while a player is near, leave 30 s after
 * nobody is within the despawn radius, belong to no group, fight back when hit and go back on their beat.
 */
@DisplayName("PatrolManager - beat cops around stations")
class PatrolManagerTest {

	private PatrolFixture     fx;
	private StationRegistry   stations;
	private CopSpawnManager   spawner;
	private NpcMarkManager    marks;
	private JavaPlugin        plugin;
	private CopPerception     perception;
	private List<Player>      online;
	private List<CopNpc>      spawned;
	private PatrolSettings    settings;
	private boolean           citizens;
	private Predicate<Entity> isNpc;
	private Location          anchor;
	private PatrolManager     manager;

	@BeforeEach
	void setUp() {
		fx         = new PatrolFixture();
		stations   = mock(StationRegistry.class);
		spawner    = mock(CopSpawnManager.class);
		marks      = mock(NpcMarkManager.class);
		plugin     = mock(JavaPlugin.class);
		perception = new CopPerception();
		online     = new ArrayList<>();
		spawned    = new ArrayList<>();
		settings   = PatrolSettings.DEFAULT;
		citizens   = true;
		isNpc      = entity -> false;
		anchor     = fx.at(0, 0);

		Station station = station(1, "Central", anchor);
		when(stations.all()).thenReturn(List.of(station));
		when(spawner.spawnAtLocation(any(), anyInt())).thenAnswer(inv -> {
			CopNpc cop = fx.cop(CopState.IDLE, 0, 0, inv.getArgument(0));
			spawned.add(cop);
			return cop;
		});
		when(fx.bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));

		manager = newManager();
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	private PatrolManager newManager() {
		Supplier<Collection<? extends Player>> players = () -> online;
		return new PatrolManager(plugin, () -> settings, stations, spawner, marks, perception, players,
		                         entity -> isNpc.test(entity), () -> citizens, () -> fx.clock[0]);
	}

	private static Station station(int id, String name, Location at) {
		Station station = mock(Station.class);
		when(station.getId()).thenReturn(id);
		when(station.getName()).thenReturn(name);
		when(station.getLocation()).thenReturn(at);
		return station;
	}

	@Test
	@DisplayName("a player 40 blocks from the anchor gets two beat cops in state PATROL and without a group")
	void spawnsCopsWhenPlayerNearStation() {
		online.add(fx.player(40, 0));

		manager.manage();

		verify(spawner, times(2)).spawnAtLocation(eq(anchor), eq(1));
		assertEquals(2, spawned.size());
		for (CopNpc cop : spawned) {
			assertEquals(CopState.PATROL, cop.getCurrentState());
			verify(cop, never()).setGroup(any());
		}
		assertEquals(2, manager.beatCops().size());
	}

	@Test
	@DisplayName("a second tick with the beat full spawns nothing more")
	void fullBeatSpawnsNoMore() {
		online.add(fx.player(40, 0));

		manager.manage();
		manager.manage();

		verify(spawner, times(2)).spawnAtLocation(any(), anyInt());
	}

	@Test
	@DisplayName("Cops_Per_Station and Tier come from the settings")
	void settingsChooseCountAndTier() {
		settings = new PatrolSettings(true, 3, 64, 96, 30, 24, 3, 6, 2, true);
		online.add(fx.player(10, 10));

		manager.manage();

		verify(spawner, times(3)).spawnAtLocation(any(), eq(2));
	}

	@Test
	@DisplayName("a player beyond Activation_Radius spawns nobody")
	void playerTooFar_noCops() {
		online.add(fx.player(70, 0));

		manager.manage();

		verify(spawner, never()).spawnAtLocation(any(), anyInt());
	}

	/** Flipped 0.21.1 (cause 6): the replacement waits out Respawn_Seconds instead of popping in on the next tick. */
	@Test
	@DisplayName("a dead beat cop is replaced once Respawn_Seconds (30) have passed, not on the next tick")
	void deadCopReplaced() {
		online.add(fx.player(5, 5));
		manager.manage();
		PatrolFixture.kill(spawned.get(0));

		manager.manage();
		verify(spawned.get(0)).destroy(any());
		verify(spawner, times(2)).spawnAtLocation(any(), anyInt());
		assertEquals(1, manager.beatCops().size());

		fx.clock[0] += 29_000;
		manager.manage();
		verify(spawner, times(2)).spawnAtLocation(any(), anyInt());

		fx.clock[0] += 1_000;
		manager.manage();
		verify(spawner, times(3)).spawnAtLocation(any(), anyInt());
		assertEquals(2, manager.beatCops().size());
	}

	@Test
	@DisplayName("a beat cop lost in the AI tick starts the same respawn wait")
	void copLostInAiTick_waitsOutRespawnSeconds() {
		online.add(fx.player(5, 5));
		manager.manage();
		PatrolFixture.kill(spawned.get(0));

		manager.aiTick();
		manager.manage();

		verify(spawner, times(2)).spawnAtLocation(any(), anyInt());
		assertEquals(1, manager.beatCops().size());
	}

	@Test
	@DisplayName("nobody within Despawn_Radius for 30 s despawns the beat; 29 s keeps it")
	void despawnsAfterNobodyNear() {
		online.add(fx.player(5, 5));
		manager.manage();
		online.clear();

		manager.manage();
		fx.clock[0] += 29_000;
		manager.manage();
		assertEquals(2, manager.beatCops().size());
		for (CopNpc cop : spawned)
			verify(cop, never()).destroy(any());

		fx.clock[0] += 1_000;
		manager.manage();

		assertTrue(manager.beatCops().isEmpty());
		for (CopNpc cop : spawned)
			verify(cop).destroy(any());
	}

	@Test
	@DisplayName("a player between Activation_Radius and Despawn_Radius keeps the beat but spawns nobody new")
	void playerBetweenRadiiKeepsCops() {
		online.add(fx.player(5, 5));
		manager.manage();
		online.clear();
		online.add(fx.player(80, 0));

		manager.manage();
		fx.clock[0] += 600_000;
		manager.manage();

		assertEquals(2, manager.beatCops().size());
		verify(spawner, times(2)).spawnAtLocation(any(), anyInt());
	}

	@Test
	@DisplayName("a player coming back resets the despawn clock")
	void returningPlayerResetsClock() {
		Player player = fx.player(5, 5);
		online.add(player);
		manager.manage();
		online.clear();
		manager.manage();
		fx.clock[0] += 25_000;
		online.add(player);
		manager.manage();
		online.clear();
		fx.clock[0] += 1_000;
		manager.manage();
		fx.clock[0] += 29_000;

		manager.manage();

		assertEquals(2, manager.beatCops().size());
	}

	@Test
	@DisplayName("an empty StationRegistry never calls the spawner")
	void noStationsNoCops() {
		when(stations.all()).thenReturn(List.of());
		online.add(fx.player(1, 1));

		manager.manage();

		verify(spawner, never()).spawnAtLocation(any(), anyInt());
		assertTrue(manager.beatCops().isEmpty());
	}

	@Test
	@DisplayName("Citizens absent: no beats, nothing spawned")
	void citizensAbsent_noCops() {
		citizens = false;
		online.add(fx.player(1, 1));

		manager.manage();

		verify(spawner, never()).spawnAtLocation(any(), anyInt());
	}

	@Test
	@DisplayName("Cops.Patrol.Enable false: no beats, and a running beat is despawned")
	void disabled_despawnsAndSpawnsNothing() {
		online.add(fx.player(1, 1));
		manager.manage();
		settings = new PatrolSettings(false, 2, 64, 96, 30, 24, 3, 6, 1, true);

		manager.manage();

		assertTrue(manager.beatCops().isEmpty());
		for (CopNpc cop : spawned)
			verify(cop).destroy(any());
		verify(spawner, times(2)).spawnAtLocation(any(), anyInt());
	}

	@Test
	@DisplayName("a Citizens NPC standing near the station does not count as a player")
	void npcPlayersDoNotCount() {
		Player fake = fx.player(1, 1);
		online.add(fake);
		isNpc = entity -> entity == fake;

		manager.manage();

		verify(spawner, never()).spawnAtLocation(any(), anyInt());
	}

	@Test
	@DisplayName("a player in another world does not count")
	void otherWorldPlayerIgnored() {
		Player far = fx.player(1, 1);
		World  other = mock(World.class);
		when(far.getWorld()).thenReturn(other);
		when(far.getLocation()).thenReturn(new Location(other, 1, 64, 1));
		online.add(far);

		manager.manage();

		verify(spawner, never()).spawnAtLocation(any(), anyInt());
	}

	@Test
	@DisplayName("a station whose world is unloaded (null location) is skipped without an error")
	void stationWithoutLocation_skipped() {
		Station broken = station(2, "Lost", null);
		when(stations.all()).thenReturn(List.of(broken));
		online.add(fx.player(1, 1));

		manager.manage();

		verify(spawner, never()).spawnAtLocation(any(), anyInt());
	}

	@Test
	@DisplayName("a removed station has its beat cops despawned")
	void removedStation_despawns() {
		online.add(fx.player(1, 1));
		manager.manage();
		when(stations.all()).thenReturn(List.of());

		manager.manage();

		assertTrue(manager.beatCops().isEmpty());
		for (CopNpc cop : spawned)
			verify(cop).destroy(any());
	}

	@Test
	@DisplayName("a spawner that fails (null) leaves the beat short without an error")
	void spawnerFailure_tolerated() {
		when(spawner.spawnAtLocation(any(), anyInt())).thenReturn(null);
		online.add(fx.player(1, 1));

		manager.manage();

		assertTrue(manager.beatCops().isEmpty());
	}

	@Test
	@DisplayName("beat cops come only from CopSpawnManager.spawnAtLocation (the factory sets the POLICE mark and SHOULD_SAVE false)")
	void beatCopsSpawnedThroughFactory() {
		online.add(fx.player(1, 1));

		manager.manage();

		verify(spawner, times(2)).spawnAtLocation(any(), anyInt());
		verifyNoMoreInteractions(spawner);
	}

	@Test
	@DisplayName("the resolver returns the beat cop for its body; null after despawn, for a stranger and for null")
	void beatCopResolvedByCopManager() {
		online.add(fx.player(1, 1));
		manager.manage();
		CopNpc beat = spawned.get(0);
		Entity body = beat.getEntity();

		assertSame(beat, manager.beatCopOf(body));
		assertNull(manager.beatCopOf(mock(Entity.class)));
		assertNull(manager.beatCopOf(null));

		manager.despawnAll();

		assertNull(manager.beatCopOf(body));
	}

	@Test
	@DisplayName("a dying beat cop (invalid, before the next aiTick) still resolves through beatCopOf for findDyingCop")
	void dyingCopStillResolves() {
		online.add(fx.player(1, 1));
		manager.manage();
		CopNpc beat = spawned.get(0);
		Entity body = beat.getEntity();
		PatrolFixture.kill(beat);

		assertSame(beat, manager.beatCopOf(body));
	}

	@Test
	@DisplayName("a hit beat cop stays in COMBAT while its attacker is around")
	void hitBeatCopKeepsFightingWhileTargetAround() {
		Player attacker = fx.player(2, 2);
		online.add(attacker);
		manager.manage();
		CopNpc beat = spawned.get(0);
		beat.setTargetPlayerId(attacker.getUniqueId());
		beat.setCombatForced(true);
		beat.transitionTo(CopState.COMBAT);

		manager.aiTick();

		assertEquals(CopState.COMBAT, beat.getCurrentState());
		verify(beat).tick(attacker);
	}

	@Test
	@DisplayName("a hit beat cop whose attacker is gone returns to PATROL, combat flag and target cleared")
	void hitBeatCopFightsThenResumesBeat() {
		Player attacker = fx.player(2, 2);
		online.add(attacker);
		manager.manage();
		CopNpc beat = spawned.get(0);
		beat.setTargetPlayerId(attacker.getUniqueId());
		beat.setCombatForced(true);
		beat.transitionTo(CopState.COMBAT);
		fx.quit(attacker);

		manager.aiTick();

		assertEquals(CopState.PATROL, beat.getCurrentState());
		assertFalse(beat.isCombatForced());
		assertNull(beat.getTargetPlayerId());
	}

	@Test
	@DisplayName("a hit beat cop whose attacker died returns to PATROL")
	void hitBeatCop_targetDead_resumesBeat() {
		Player attacker = fx.player(2, 2);
		online.add(attacker);
		manager.manage();
		CopNpc beat = spawned.get(0);
		beat.setTargetPlayerId(attacker.getUniqueId());
		beat.transitionTo(CopState.COMBAT);
		when(attacker.isDead()).thenReturn(true);

		manager.aiTick();

		assertEquals(CopState.PATROL, beat.getCurrentState());
	}

	@Test
	@DisplayName("cnc-integ INT-1: a hit beat cop stands down when its target's squad has cease-fire (surrender)")
	void hitBeatCop_targetCeaseFire_standsDown() {
		Player attacker = fx.player(2, 2);
		online.add(attacker);
		manager.manage();
		CopNpc beat = spawned.get(0);
		org.luckyraven.gangland.copsncrooks.npc.police.CopGroup group = mock(org.luckyraven.gangland.copsncrooks.npc.police.CopGroup.class);
		when(group.isCeaseFire()).thenReturn(true);
		manager.setGroupLookup(id -> group);
		beat.setTargetPlayerId(attacker.getUniqueId());
		beat.setCombatForced(true);
		beat.transitionTo(CopState.COMBAT);

		manager.aiTick();

		assertEquals(CopState.PATROL, beat.getCurrentState());
		assertFalse(beat.isCombatForced());
		assertNull(beat.getTargetPlayerId());
		verify(beat, never()).tick(attacker);
	}

	@Test
	@DisplayName("RETURNING or IDLE beat cops are put back on PATROL")
	void returningOrIdle_backToPatrol() {
		online.add(fx.player(2, 2));
		manager.manage();
		CopNpc returning = spawned.get(0);
		CopNpc idle      = spawned.get(1);
		returning.transitionTo(CopState.RETURNING);
		idle.transitionTo(CopState.IDLE);

		manager.aiTick();

		assertEquals(CopState.PATROL, returning.getCurrentState());
		assertEquals(CopState.PATROL, idle.getCurrentState());
	}

	@Test
	@DisplayName("a beat cop in PATROL is ticked with no target; in WARNING with the warned player")
	void aiTickPassesTargets() {
		Player warned = fx.player(2, 2);
		online.add(warned);
		manager.manage();
		CopNpc patrolling = spawned.get(0);
		CopNpc warning    = spawned.get(1);
		warning.setTargetPlayerId(warned.getUniqueId());
		warning.transitionTo(CopState.WARNING);

		manager.aiTick();

		verify(patrolling).tick(null);
		verify(warning).tick(warned);
	}

	@Test
	@DisplayName("a beat cop that turned invalid is destroyed, forgotten and replaced once Respawn_Seconds have passed")
	void invalidCopPruned() {
		online.add(fx.player(2, 2));
		manager.manage();
		CopNpc gone = spawned.get(0);
		PatrolFixture.kill(gone);

		manager.aiTick();

		assertEquals(1, manager.beatCops().size());
		verify(gone, never()).tick(any());
		verify(gone).destroy(any());
		// flipped 0.21.1 (cause 6): not on the next manage, after the station's respawn wait
		fx.clock[0] += 30_000;
		manager.manage();
		assertEquals(2, manager.beatCops().size());
	}

	@Test
	@DisplayName("despawnAll destroys every beat cop and forgets them")
	void despawnAll_destroysAll() {
		online.add(fx.player(2, 2));
		manager.manage();

		manager.despawnAll();

		assertTrue(manager.beatCops().isEmpty());
		for (CopNpc cop : spawned)
			verify(cop).destroy(any());
	}

	@Test
	@DisplayName("onShutdown and onClear despawn every beat cop (never leave Citizens NPCs behind)")
	void shutdownAndClear_despawn() {
		online.add(fx.player(2, 2));
		manager.manage();
		manager.onShutdown();
		assertTrue(manager.beatCops().isEmpty());

		manager.manage();
		assertEquals(2, manager.beatCops().size());
		manager.onClear();
		assertTrue(manager.beatCops().isEmpty());
	}

	@Test
	@DisplayName("onInitialize starts one repeating task on the main thread; onShutdown cancels it")
	void lifecycleStartsAndCancelsTask() {
		BukkitTask task = mock(BukkitTask.class);
		when(fx.bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(task);

		manager.onInitialize(true);
		verify(fx.bukkit.scheduler(), times(1)).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(),
		                                                     anyLong());

		manager.onShutdown();
		verify(task).cancel();
	}

	@Test
	@DisplayName("beatCopSees: a beat cop in range that acquires the player; none in range or none acquiring is false")
	void beatCopSees() {
		Player near = fx.player(10, 0);
		Player far  = fx.player(60, 0);
		online.add(near);
		manager.manage();

		assertTrue(manager.beatCopSees(near, 24.0));
		assertFalse(manager.beatCopSees(far, 24.0));

		for (CopNpc cop : spawned)
			when(cop.canSee(any(LivingEntity.class), anyDouble())).thenReturn(false);
		assertFalse(manager.beatCopSees(near, 24.0));
	}

	@Test
	@DisplayName("beatCopSees honours the perception lock-on filter (a disguised player is not seen)")
	void beatCopSees_lockOnFilter() {
		Player near = fx.player(10, 0);
		online.add(near);
		manager.manage();
		perception.addLockOnFilter((cop, player) -> false);

		assertFalse(manager.beatCopSees(near, 24.0));
	}
}
