package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BreatherSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DispatchSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.PendingUnit;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cops N Crooks 0.16 (CONTRACTS C8, Rulings R15-R21): with dispatch and the breather on, every missing cop is a unit
 * queued at the nearest station's ETA (the ring at once without a station), radioed once per batch; a wiped squad
 * waits out the breather; a RESTORE start skips the crime-scene seed and waits out the rejoin grace; a hand-off bias
 * travels with each unit and seeds the squad when it spawns. Composition slots carry their own tier.
 */
@DisplayName("CopManager - dispatch from stations, mixed tiers and the post-wipe breather")
class CopManagerDispatchTest {

	private static final CopRole COMMANDER = role("Commander", 10, true);
	private static final CopRole POINTMAN  = role("Pointman", 5, false);
	private static final CopRole DEFENDER  = role("Defender", 4, false);
	private static final CopRole MARKSMAN  = role("Marksman", 3, false);
	private static final CopRole ASSAULT   = role("Assault", 0, false);

	private CopManagerFixture fx;
	private CopManager        manager;
	private Player            player;
	private UUID              playerId;
	private Wanted            wanted;
	private final List<Station> stations = new ArrayList<>();

	private static CopRole role(String name, int priority, boolean commander) {
		return new CopRole(name, name, NpcFanPlacement.ANY, null, null, 1.0, null, priority, null, 1.0, 0, null, 0, 60,
		                   false, commander);
	}

	@BeforeEach
	void setUp() {
		fx = new CopManagerFixture();
		when(fx.provider.getDispatchSettings()).thenReturn(DispatchSettings.DEFAULT);
		when(fx.provider.getBreatherSettings()).thenReturn(BreatherSettings.DEFAULT);
		when(fx.world.getName()).thenReturn("world");
		when(fx.stations.all()).thenAnswer(inv -> new ArrayList<>(stations));
		manager  = fx.manager;
		player   = fx.player(10, 10);
		playerId = player.getUniqueId();
		wanted   = CopManagerFixture.wanted(2);
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	/** Northside, 200 blocks east of the player: a 20 s ETA. */
	private Station northside() {
		Station station = new Station(1, "Northside", "world", 210, 64, 10, 0f, null);
		stations.add(station);
		return station;
	}

	private CopGroup group() {
		return manager.groupFor(playerId);
	}

	private List<PendingUnit> spawnedUnits(int times) {
		ArgumentCaptor<PendingUnit> units = ArgumentCaptor.forClass(PendingUnit.class);
		verify(fx.spawner, times(times)).spawnUnit(eq(player), units.capture(), any());
		return units.getAllValues();
	}

	@Test
	@DisplayName("a crime queues the missing cops at the station's ETA and spawns nothing yet")
	void crime_enqueuesUnitsWithTheStationEta_andSpawnsNothingYet() {
		Station station = northside();
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, wanted);

		assertEquals(2, group().pendingCount());
		for (PendingUnit unit : group().getPending()) {
			assertEquals(21_000L, unit.arriveAt(), "now 1 s + 20 s ETA");
			assertSame(station, unit.station());
			assertTrue(unit.fromStation());
		}
		assertTrue(group().getCops().isEmpty());
		verify(fx.spawner, never()).spawnUnit(any(), any(), any());
		verify(fx.spawner, never()).spawnNearPlayer(any(), anyInt(), any(), any());
		assertTrue(group().unitsEnRoute(fx.clock[0]));
	}

	@Test
	@DisplayName("due units spawn pursuing the player; the batch was radioed once with count, station and ETA")
	void dueUnits_spawn_andEnRouteIsRadioedOnce() {
		northside();
		manager.onWantedStart(player, wanted);
		manager.spawnTick(playerId, wanted);
		fx.clock[0] = 10_000L;
		manager.spawnTick(playerId, wanted);
		verify(fx.spawner, never()).spawnUnit(any(), any(), any());

		fx.clock[0] = 21_000L;
		manager.spawnTick(playerId, wanted);

		assertEquals(2, group().getCops().size());
		assertEquals(0, group().pendingCount());
		for (CopNpc cop : group().getCops()) {
			assertEquals(CopState.PURSUING, cop.getCurrentState());
			assertEquals(playerId, cop.getTargetPlayerId());
		}
		verify(fx.radio, times(1)).dispatch(eq(group()), eq(player), eq("Dispatch_En_Route"), eq(2), anyString(),
		                                    eq(Map.of("count", "2", "station", "Northside", "eta", "20")));
	}

	@Test
	@DisplayName("no station in the world: ring units arrive at once, not from a station, and no en-route line")
	void noStation_ringUnitsArriveAtOnce_fromStationFalse() {
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, wanted);

		assertEquals(2, group().getCops().size());
		for (PendingUnit unit : spawnedUnits(2)) {
			assertFalse(unit.fromStation());
			assertEquals(1_000L, unit.arriveAt());
		}
		verify(fx.radio, never()).dispatch(any(), any(), eq("Dispatch_En_Route"), anyInt(), anyString(), anyMap());
	}

	@Test
	@DisplayName("a squad wiped inside the window waits out the breather (13 s at two stars) and radios Wipe_Refill")
	void wipe_addsTheBreather_andRadiosWipeRefill() {
		manager.onWantedStart(player, wanted);
		manager.spawnTick(playerId, wanted);
		for (CopNpc cop : group().getCops()) when(cop.isMarkedForRemoval()).thenReturn(true);
		group().recordCasualty(1_500L);

		fx.clock[0] = 2_000L;
		manager.spawnTick(playerId, wanted);

		assertEquals(15_000L, group().getBreatherUntil());
		assertTrue(group().getCops().isEmpty());
		assertEquals(2, group().pendingCount());
		verify(fx.radio).dispatch(eq(group()), eq(player), eq("Wipe_Refill"), eq(2), anyString(),
		                          eq(Map.of("eta", "13")));

		fx.clock[0] = 15_000L;
		manager.spawnTick(playerId, wanted);

		assertEquals(2, group().getCops().size());
	}

	/**
	 * Fix round 1: at five stars the breather (6 s) is shorter than the wipe window (10 s), so on the tick it ends the
	 * squad is still empty (the refill spawns after the dispatch pass) and the old casualty is still in the window. That
	 * is the same wipe, not a new one: the breather must not be pushed out again, and a cop lost right after the refill
	 * comes back at once.
	 */
	@Test
	@DisplayName("a breather shorter than the wipe window is not renewed when it ends; a later loss refills at once")
	void wipe_breatherShorterThanWindow_isNotRenewed() {
		Wanted five = CopManagerFixture.wanted(5);
		manager.onWantedStart(player, five);
		manager.spawnTick(playerId, five);
		for (CopNpc cop : group().getCops()) when(cop.isMarkedForRemoval()).thenReturn(true);
		group().recordCasualty(1_500L);

		fx.clock[0] = 2_000L;
		manager.spawnTick(playerId, five);
		assertEquals(8_000L, group().getBreatherUntil(), "6 s at five stars");

		fx.clock[0] = 8_000L;
		manager.spawnTick(playerId, five);

		assertEquals(8_000L, group().getBreatherUntil(), "the wipe already seen does not start a second breather");
		assertEquals(2, group().getCops().size());

		CopNpc lost = group().getCops().get(0);
		when(lost.isMarkedForRemoval()).thenReturn(true);
		group().recordCasualty(8_500L);
		fx.clock[0] = 9_000L;
		manager.spawnTick(playerId, five);

		assertEquals(2, group().getCops().size(), "the lost cop is refilled with no extra hold");
		assertEquals(0, group().pendingCount());
		verify(fx.radio, times(1)).dispatch(any(), any(), eq("Wipe_Refill"), anyInt(), anyString(), anyMap());
	}

	/**
	 * Fix round 1: a backup unit whose station ETA (40 s) outlasts the backup (30 s) is still queued when the backup
	 * runs out. It is the surplus, so it is dropped from the queue instead of arriving later and staying for good.
	 */
	@Test
	@DisplayName("a backup unit still en route when the backup runs out is called off, never spawned")
	void backupExpired_queuedExtraIsCalledOff() {
		manager.onWantedStart(player, wanted);
		manager.spawnTick(playerId, wanted);
		assertEquals(2, group().getCops().size());
		stations.add(new Station(2, "Faraway", "world", 510, 64, 10, 0f, null));

		assertTrue(group().requestBackup(fx.clock[0], fx.provider.getBackupSettings()));
		manager.spawnTick(playerId, wanted);
		assertEquals(1, group().pendingCount());
		assertEquals(41_000L, group().getPending().get(0).arriveAt(), "40 s ETA, past the 30 s backup");

		fx.clock[0] = 31_000L;
		manager.spawnTick(playerId, wanted);

		assertEquals(0, group().pendingCount(), "the extra still en route is called off");
		assertEquals(0, group().getPendingRelease());

		fx.clock[0] = 41_000L;
		manager.spawnTick(playerId, wanted);

		assertEquals(2, group().getCops().size());
		verify(fx.spawner, times(2)).spawnUnit(eq(player), any(), any());
		for (CopNpc cop : group().getCops()) assertFalse(cop.getCurrentState() == CopState.RETURNING);
	}

	/** Characterization pin: also passes on the pre-change code (no breather existed); it pins that only a casualty makes a wipe. */
	@Test
	@DisplayName("cops lost without a recent casualty are no wipe: the refill comes at once, no breather")
	void noWipe_noBreather() {
		manager.onWantedStart(player, wanted);
		manager.spawnTick(playerId, wanted);
		for (CopNpc cop : group().getCops()) when(cop.isMarkedForRemoval()).thenReturn(true);

		fx.clock[0] = 2_000L;
		manager.spawnTick(playerId, wanted);

		assertEquals(0L, group().getBreatherUntil());
		assertEquals(2, group().getCops().size());
		verify(fx.radio, never()).dispatch(any(), any(), eq("Wipe_Refill"), anyInt(), anyString(), anyMap());
	}

	@Test
	@DisplayName("a RESTORE start seeds no crime scene and holds every unit through the 15 s rejoin grace")
	void restoreStart_noSeed_andGraceDelay() {
		manager.onWantedStart(player, wanted, WantedCause.RESTORE);

		assertFalse(group().getSquad().hasFreshSighting());
		assertNull(group().getSquad().lastKnownLocation());
		assertEquals(16_000L, group().getBreatherUntil());

		manager.spawnTick(playerId, wanted);
		assertEquals(2, group().pendingCount());
		assertTrue(group().getCops().isEmpty(), "no station, but the grace still holds them");

		fx.clock[0] = 16_000L;
		manager.spawnTick(playerId, wanted);
		assertEquals(2, group().getCops().size());
	}

	/** Characterization pin: Dispatch off keeps the 0.15 start (crime-scene seed, no hold), as the pre-change code did. */
	@Test
	@DisplayName("a RESTORE start with dispatch off is a 0.15 start: the crime scene is seeded, no grace")
	void restoreStart_dispatchOff_isTheLegacyStart() {
		when(fx.provider.getDispatchSettings()).thenReturn(DispatchSettings.DISABLED);

		manager.onWantedStart(player, wanted, WantedCause.RESTORE);

		assertTrue(group().getSquad().hasFreshSighting());
		assertEquals(0L, group().getBreatherUntil());
	}

	@Test
	@DisplayName("queued units count toward the target: a second pass before they arrive queues nothing more")
	void pendingUnits_countTowardTheTarget_noDoubleEnqueue() {
		northside();
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, wanted);
		fx.clock[0] = 2_000L;
		manager.spawnTick(playerId, wanted);

		assertEquals(2, group().pendingCount());
	}

	@Test
	@DisplayName("a unit that finds no spot is requeued and tried again on the next pass")
	void failedSpawn_isRequeued() {
		when(fx.spawner.spawnUnit(any(), any(), any())).thenReturn(null);
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, wanted);

		assertEquals(2, group().pendingCount());
		assertTrue(group().getCops().isEmpty());

		when(fx.spawner.spawnUnit(any(), any(), any())).thenAnswer(inv -> fx.cop(CopState.IDLE, 0, 0));
		fx.clock[0] = 2_000L;
		manager.spawnTick(playerId, wanted);

		assertEquals(0, group().pendingCount());
		assertEquals(2, group().getCops().size());
		verify(fx.spawner, times(4)).spawnUnit(eq(player), any(), any());
	}

	@Test
	@DisplayName("three stars: each composition slot spawns at its own tier (Pointman 2, Marksman 3)")
	void threeStars_slotsGetTheirTiers() {
		Wanted three = CopManagerFixture.wanted(3);
		when(fx.spawner.getTargetCopCount(anyInt())).thenReturn(5);
		when(fx.provider.getSquadComposition(3)).thenReturn(List.of(COMMANDER, POINTMAN, DEFENDER, MARKSMAN, ASSAULT));
		when(fx.provider.getSquadTiers(3)).thenReturn(List.of(3, 2, 3, 3, 2));
		manager.onWantedStart(player, three);

		manager.spawnTick(playerId, three);

		Map<String, Integer> tiers = new java.util.HashMap<>();
		for (PendingUnit unit : spawnedUnits(5)) tiers.put(unit.role().name(), unit.tier());
		assertEquals(Map.of("Commander", 3, "Pointman", 2, "Defender", 3, "Marksman", 3, "Assault", 2), tiers);
	}

	@Test
	@DisplayName("legacy path (dispatch and breather off): slots still get their tiers, clamped to the top tier")
	void legacyPath_slotTiers_clampedToMaxTier() {
		when(fx.provider.getDispatchSettings()).thenReturn(DispatchSettings.DISABLED);
		when(fx.provider.getBreatherSettings()).thenReturn(BreatherSettings.DISABLED);
		when(fx.provider.getMaxTier()).thenReturn(4);
		when(fx.provider.getSquadComposition(2)).thenReturn(List.of(COMMANDER, POINTMAN));
		when(fx.provider.getSquadTiers(2)).thenReturn(List.of(6, 0));
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, wanted);

		verify(fx.spawner).spawnNearPlayer(eq(player), eq(4), any(), eq(COMMANDER));
		verify(fx.spawner).spawnNearPlayer(eq(player), eq(3), any(), eq(POINTMAN)); // 0 = the star's tier (fixture 3)
	}

	@Test
	@DisplayName("with a hand-off bias active, new units carry it and seed the squad with its last sighting + a tip-off")
	void biasActive_newUnitsAreSeededWithTipOff() {
		manager.onWantedStart(player, wanted);
		Location  lastSeen = new Location(fx.world, 50, 64, 10);
		SpawnBias bias     = new SpawnBias(new Vector(1, 0, 0), lastSeen, 11_000L, 60.0);
		group().setBias(bias);

		manager.spawnTick(playerId, wanted);

		for (PendingUnit unit : spawnedUnits(2)) assertSame(bias, unit.bias());
		assertEquals(lastSeen, group().getSquad().lastKnownLocation());
		assertTrue(group().tippedOffWithin(fx.clock[0], 0L));
	}

	@Test
	@DisplayName("a 20 s ETA outlives the 10 s bias: the unit still spawns with it and seeds lastSeen")
	void biasSurvivesALongEta() {
		northside();
		manager.onWantedStart(player, wanted);
		Location  lastSeen = new Location(fx.world, 60, 64, 10);
		SpawnBias bias     = new SpawnBias(new Vector(1, 0, 0), lastSeen, 11_000L, 60.0);
		group().setBias(bias);
		manager.spawnTick(playerId, wanted);

		fx.clock[0] = 21_000L;
		assertNull(group().biasAt(fx.clock[0]), "the group's bias has expired");
		manager.spawnTick(playerId, wanted);

		for (PendingUnit unit : spawnedUnits(2)) assertSame(bias, unit.bias());
		assertEquals(lastSeen, group().getSquad().lastKnownLocation());
		assertTrue(group().tippedOffWithin(21_000L, 0L));
	}

	@Test
	@DisplayName("a held backup never holds the base squad: a lost cop is still refilled")
	void backupHeld_baseSquadStillRefills() {
		manager.onWantedStart(player, wanted);
		manager.spawnTick(playerId, wanted);
		CopNpc lost = group().getCops().get(0);
		when(lost.isMarkedForRemoval()).thenReturn(true);
		group().setBackupHeld(true);

		fx.clock[0] = 2_000L;
		manager.spawnTick(playerId, wanted);

		assertEquals(2, group().getCops().size());
		assertFalse(group().getCops().contains(lost));
		verify(fx.spawner, atLeastOnce()).spawnUnit(eq(player), any(), any());
	}

	@Test
	@DisplayName("the wanted level ending clears the group's queue")
	void wantedEnd_clearsTheQueue() {
		northside();
		manager.onWantedStart(player, wanted);
		manager.spawnTick(playerId, wanted);
		CopGroup group = group();
		assertEquals(2, group.pendingCount());

		manager.onWantedEnd(player);

		assertEquals(0, group.pendingCount());
		assertFalse(group.unitsEnRoute(fx.clock[0]));
	}
}
