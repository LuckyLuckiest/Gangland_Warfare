package org.luckyraven.gangland.copsncrooks.npc.police.spawn;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.HandoffSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.PendingUnit;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpcFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CONTRACTS C8 {@code spawnUnit}: a due unit comes out of its station's spawners when one is out of sight, else from a
 * hidden ring spot ahead of its hand-off bias or on the station side, else from any hidden ring spot, else from the
 * 0.15 {@code spawnNearPlayer}. Sight is stubbed on the ray's direction (a coordinate rule, never call order), so the
 * random ring angles cannot change the outcome.
 */
@DisplayName("CopSpawnManager - spawning a dispatched unit out of sight")
class CopSpawnManagerUnitTest {

	private static final int GROUND_Y = 64;

	private static final CopRole MARKSMAN = new CopRole("Marksman", "Marksman", NpcFanPlacement.ANY, null, null, 1.0,
	                                                    null, 3, null, 1.0, 0, null, 0, 60, false, false);

	private World           world;
	private CopConfigProvider spawnConfig;
	private Player          player;
	private CopSpawnManager spawnManager;
	private CopNpcFactory   factory;
	/** Which ray directions a block stops: the spot the ray points at is hidden. */
	private Predicate<Vector> blocked = dir -> false;
	/** The Y the world reports as highest everywhere but the suspect's own column. */
	private int               roofElsewhere = GROUND_Y - 1;
	private int               roofAbovePlayer = GROUND_Y - 1;

	@BeforeEach
	void setUp() {
		CopConfigProvider config = mock(CopConfigProvider.class);
		spawnConfig = config;
		when(config.getMinSpawnDistance()).thenReturn(10.0);
		when(config.getMaxSpawnDistance()).thenReturn(20.0);
		when(config.getPhase1MinDistance()).thenReturn(15.0);
		when(config.getSpawnRadiusShrinkStep()).thenReturn(5.0);
		when(config.getVerticalSearchRange()).thenReturn(10);
		when(config.getMaxSpawnYDiff()).thenReturn(4.0);
		when(config.getSpawnerMaxYDiff()).thenReturn(16.0);
		when(config.getMinOpenHorizontalSides()).thenReturn(2);
		when(config.getSpawnerPreferenceRadius()).thenReturn(80.0);
		when(config.getVisibilityCheckDistance()).thenReturn(48.0);
		// the leash: a spawn spot must stay within 0.75 x 80 = 60 blocks (3D) of the suspect
		when(config.getPursuitMaxDistance()).thenReturn(80.0);
		// many tries per ring, so a 120 degree cone is all but certain to be hit (2/3 ^ 120 misses)
		when(config.getSpawnPhase1Attempts()).thenReturn(30);
		when(config.getSpawnPhase2Attempts()).thenReturn(30);
		CopLoader loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(config);

		@SuppressWarnings("unchecked") IRepository<CopSpawner> repository = mock(IRepository.class);
		spawnManager = new CopSpawnManager(mock(JavaPlugin.class), loader, mock(NpcMarkManager.class),
		                                   mock(BartizanNpcWeapons.class), mock(DownedTargetFilter.class), repository,
		                                   mock(DetainmentService.class), mock(CuffLockRegistry.class));
		factory = mock(CopNpcFactory.class);
		when(factory.createCop(any(), anyInt(), anyBoolean(), any())).thenAnswer(inv -> mock(CopNpc.class));
		spawnManager.copNpcFactory = factory;

		world = mock(World.class);
		when(world.getName()).thenReturn("world");
		when(world.getMaxHeight()).thenReturn(320);
		when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
		when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> block(inv.getArgument(1)));
		when(world.getHighestBlockYAt(anyInt(), anyInt())).thenAnswer(inv -> {
			int x = inv.getArgument(0);
			int z = inv.getArgument(1);
			return x == 0 && z == 0 ? roofAbovePlayer : roofElsewhere;
		});
		when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class),
		                          anyBoolean()))
				// a blocked ray hits at its full length (the hand-off lead's wall check reads the hit position)
				.thenAnswer(inv -> {
					Vector dir = inv.getArgument(1);
					if (!blocked.test(dir)) return null;
					Location from = inv.getArgument(0);
					return new RayTraceResult(from.toVector().add(dir.clone().multiply((double) inv.getArgument(2))));
				});

		player = mock(Player.class);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 0.5, GROUND_Y, 0.5));
		when(player.getEyeLocation()).thenReturn(new Location(world, 0.5, GROUND_Y + 1.62, 0.5));
		when(world.getPlayers()).thenReturn(List.of(player));
	}

	private static PendingUnit unit(Station station, SpawnBias bias) {
		return new PendingUnit(MARKSMAN, 3, 0L, station, bias);
	}

	private Location stationSpawner(double x, double z, int stationId) {
		Location at = new Location(world, x, GROUND_Y, z);
		spawnManager.setSpawnerLocation(at);
		spawnManager.assignStation(spawnManager.ID, stationId);
		return at;
	}

	/** The ring spot createCop got (validated after spawn = a ring spot). */
	private Location ringSpot() {
		ArgumentCaptor<Location> spot = ArgumentCaptor.forClass(Location.class);
		verify(factory).createCop(spot.capture(), eq(3), eq(true), eq(MARKSMAN));
		return spot.getValue();
	}

	/** Horizontal angle in degrees between {@code dir} and {@code spot - player}. */
	private static double angle(Vector dir, Location spot) {
		Vector offset = new Vector(spot.getX() - 0.5, 0, spot.getZ() - 0.5);
		return Math.toDegrees(new Vector(dir.getX(), 0, dir.getZ()).angle(offset));
	}

	@Test
	@DisplayName("the station's spawner out of sight wins over a nearer one in view")
	void stationSpawnerOutOfSight_wins() {
		Station station = new Station(1, "Northside", "world", 200, GROUND_Y, 0.5, 0f, null);
		Location hidden = stationSpawner(30.5, 0.5, 1);    // east: rays east are blocked
		stationSpawner(-20.5, 0.5, 1);                     // west and nearer, in plain view
		blocked = dir -> dir.getX() > 0;

		CopNpc cop = spawnManager.spawnUnit(player, unit(station, null), loc -> true);

		assertNotNull(cop);
		verify(factory).createCop(hidden, 3, false, MARKSMAN);
	}

	@Test
	@DisplayName("a station spawner in view is skipped for a hidden ring spot on the station side")
	void visibleSpawnerSkipped_hiddenRingOnTheStationSide() {
		Station  station = new Station(1, "Northside", "world", 200.5, GROUND_Y, 0.5, 0f, null); // due east
		Location visible = stationSpawner(-20.5, 0.5, 1);
		// every spot hidden except straight west, where the station's spawner stands in view
		blocked = dir -> dir.getX() > -0.98;

		spawnManager.spawnUnit(player, unit(station, null), loc -> true);

		verify(factory, never()).createCop(eq(visible), anyInt(), anyBoolean(), any());
		assertTrue(angle(new Vector(1, 0, 0), ringSpot()) <= 60.0, "on the station side");
	}

	@Test
	@DisplayName("an active bias puts the hidden ring spot ahead of the suspect's heading")
	void activeBias_ringSpotIsAhead() {
		when(spawnConfig.getHandoffSettings()).thenReturn(new HandoffSettings(true, 2, 10, 60.0, 24.0, 0.0)); // the 0.16 ring ahead
		blocked = dir -> true;
		SpawnBias bias = new SpawnBias(new Vector(0, 0, -1), new Location(world, 0.5, GROUND_Y, 0.5), 10_000L, 60.0);

		spawnManager.spawnUnit(player, unit(null, bias), loc -> true);

		assertTrue(angle(new Vector(0, 0, -1), ringSpot()) <= 60.0, "ahead (north)");
	}

	@Test
	@DisplayName("a suspect indoors with only open street around him: the hidden ring retries taking any roof")
	void indoorSuspect_hiddenRingRetriesAnyRoof() {
		blocked = dir -> true;
		roofAbovePlayer = GROUND_Y + 6;

		Location spot = spawnManager.hiddenRing(player, loc -> true);

		assertNotNull(spot);
		assertTrue(Math.abs(spot.getY() - GROUND_Y) <= 4.0);
	}

	@Test
	@DisplayName("a removed station (no spawners left) falls back to the hidden ring")
	void removedStation_fallsBackToTheRing() {
		Station gone = new Station(9, "Old Precinct", "world", -200.5, GROUND_Y, 0.5, 0f, null);
		blocked = dir -> true;

		CopNpc cop = spawnManager.spawnUnit(player, unit(gone, null), loc -> true);

		assertNotNull(cop);
		assertNotNull(ringSpot());
	}

	/** Flipped 0.21.1 (owner ruling R6): a spawner in front of his eyes with a clear line is never used. */
	@Test
	@DisplayName("nothing out of sight: the spawnNearPlayer fallback skips a spawner he is looking at for a spot outside his view")
	void nothingHidden_legacyPath() {
		Location spawner = new Location(world, 5.5, GROUND_Y, 5.5); // 45 degrees off his facing (+Z), clear line
		spawnManager.setSpawnerLocation(spawner); // unassigned
		blocked = dir -> false;

		CopNpc cop = spawnManager.spawnUnit(player, unit(null, null), loc -> true);

		assertNotNull(cop);
		verify(factory, never()).createCop(eq(spawner), anyInt(), anyBoolean(), any());
		assertTrue(angle(new Vector(0, 0, 1), ringSpot()) >= 60.0, "outside his view cone");
	}

	@Test
	@DisplayName("R6: the dispatch-off path (spawnNearPlayer) never takes a spawner straight ahead of him; one behind is fine")
	void spawnNearPlayer_spawnerInView_skipped_behindTaken() {
		Location ahead  = new Location(world, 0.5, GROUND_Y, 20.5);  // straight ahead (+Z), clear line
		spawnManager.setSpawnerLocation(ahead);
		blocked = dir -> false;

		spawnManager.spawnNearPlayer(player, 3, loc -> true, MARKSMAN);
		verify(factory, never()).createCop(eq(ahead), anyInt(), anyBoolean(), any());

		Location behind = new Location(world, 0.5, GROUND_Y, -10.5);
		spawnManager.setSpawnerLocation(behind);
		spawnManager.spawnNearPlayer(player, 3, loc -> true, MARKSMAN);
		verify(factory).createCop(behind, 3, false, MARKSMAN);
	}

	@Test
	@DisplayName("cause 2: a station spawner past 0.75 x Pursuit.Max_Distance (3D) is not chosen, even out of sight")
	void stationSpawnerBeyondTheLeashMargin_skipped() {
		Station  station = new Station(1, "Northside", "world", 200, GROUND_Y, 0.5, 0f, null);
		Location far     = new Location(world, 79.5, GROUND_Y + 15, 0.5); // 80.9 blocks: leashes in the second it spawns
		spawnManager.setSpawnerLocation(far);
		spawnManager.assignStation(spawnManager.ID, 1);
		blocked = dir -> true;

		assertNotNull(spawnManager.spawnUnit(player, unit(station, null), loc -> true));

		verify(factory, never()).createCop(eq(far), anyInt(), anyBoolean(), any());
		assertNotNull(ringSpot());
	}

	@Test
	@DisplayName("cause 2: the fallback's nearest spawner is skipped past the leash margin too")
	void fallbackSpawnerBeyondTheLeashMargin_skipped() {
		Location far = new Location(world, 0.5, GROUND_Y, -69.5); // behind him, clear line, 70 blocks
		spawnManager.setSpawnerLocation(far);
		blocked = dir -> false;

		assertNotNull(spawnManager.spawnUnit(player, unit(null, null), loc -> true));

		verify(factory, never()).createCop(eq(far), anyInt(), anyBoolean(), any());
	}

	@Test
	@DisplayName("cause 2 / T-423: a biased unit takes the hidden ring toward its lead before a hidden station spawner behind the heading")
	void biasedUnit_ringAheadBeforeAStationSpawnerBehind() {
		Station  station = new Station(1, "Northside", "world", -200.5, GROUND_Y, 0.5, 0f, null);
		Location behind  = stationSpawner(-30.5, 0.5, 1); // west; he runs east
		blocked = dir -> true;
		SpawnBias bias = new SpawnBias(new Vector(1, 0, 0), new Location(world, 0.5, GROUND_Y, 0.5), 10_000L, 60.0);

		spawnManager.spawnUnit(player, unit(station, bias), loc -> true);

		verify(factory, never()).createCop(eq(behind), anyInt(), anyBoolean(), any());
		// lead x=23.5 (seen at him, running east): midway, x=12
		assertTrue(horizontal(ringSpot(), 12.0, 0.5) <= CopSpawnManager.BETWEEN_RADIUS + 1e-9, "toward the lead (east)");
	}

	/** Coincident pass on the pre-change stub (which returned null); pins the requeue signal when every step fails. */
	@Test
	@DisplayName("nothing anywhere: null, so the unit is requeued")
	void nothingAnywhere_isNull() {
		when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

		assertNull(spawnManager.spawnUnit(player, unit(null, null), loc -> true));
		verify(factory, never()).createCop(any(), anyInt(), anyBoolean(), any());
	}

	@Test
	@DisplayName("a bias is never re-read from the clock: an expired one still steers its unit")
	void expiredBias_stillSteers() {
		when(spawnConfig.getHandoffSettings()).thenReturn(new HandoffSettings(true, 2, 10, 60.0, 24.0, 0.0)); // the 0.16 ring ahead
		blocked = dir -> true;
		SpawnBias bias = new SpawnBias(new Vector(1, 0, 0), new Location(world, 0.5, GROUND_Y, 0.5), 0L, 60.0);

		spawnManager.spawnUnit(player, unit(null, bias), loc -> true);

		Location spot = ringSpot();
		assertTrue(angle(new Vector(1, 0, 0), spot) <= 60.0);
		assertSame(world, spot.getWorld());
	}

	/** Seen running east from {@code lastSeenX} (x only), so the R5 lead lies 24 blocks further east. */
	private SpawnBias runEastFrom(double lastSeenX) {
		return new SpawnBias(new Vector(1, 0, 0), new Location(world, lastSeenX, GROUND_Y, 0.5), 10_000L, 60.0);
	}

	private static double horizontal(Location a, double x, double z) {
		return Math.hypot(a.getX() - x, a.getZ() - z);
	}

	@Test
	@DisplayName("T-423: he doubled back west past the lead: the unit spawns between him and the lead, not ahead east")
	void handoffUnit_spawnsBetweenHimAndTheLead() {
		blocked = dir -> true;
		// last seen at x=-40.5 running east: the lead is x=-17.5 (24 on, a block short of the wall at the ray's end);
		// he stands at x=0.5, so the ring centre is the midpoint, x=-8.5
		spawnManager.spawnUnit(player, unit(null, runEastFrom(-40.5)), loc -> true);

		// every spot of the ring ahead (within 60 degrees of east, 10-20 blocks off) is over 16 blocks from x=-8.5
		Location spot = ringSpot();
		assertTrue(horizontal(spot, -8.5, 0.5) <= CopSpawnManager.BETWEEN_RADIUS + 1e-9,
		           "around the between point, toward the lead (west), not ahead of the heading (east): " + spot);
	}

	@Test
	@DisplayName("T-423: a lead far off: the between point is clamped inside the spawn range of him (Spawn.Max_Distance)")
	void handoffUnit_farLead_centreClampedToTheSpawnRange() {
		blocked = dir -> true;
		// lead x=-177.5: the midpoint (x=-88.5) is far outside the ring; clamped to Max_Distance (20) of him, x=-19.5
		spawnManager.spawnUnit(player, unit(null, runEastFrom(-200.5)), loc -> true);

		Location spot = ringSpot();
		assertTrue(horizontal(spot, -19.5, 0.5) <= CopSpawnManager.BETWEEN_RADIUS + 1e-9, "around the clamped point: " + spot);
		assertTrue(horizontal(spot, 0.5, 0.5) <= 20.0 + 1e-9, "inside the spawn range");
	}

	@Test
	@DisplayName("T-423 / R6: every spot between him and the lead is in his view: none is taken, the unit falls back ahead")
	void handoffUnit_betweenSpotsInView_fallBackToTheRingAhead() {
		// west (toward the lead) lies open in front of him, east is walled off
		blocked = dir -> dir.getX() > 0;
		when(player.getEyeLocation()).thenReturn(new Location(world, 0.5, GROUND_Y + 1.62, 0.5, 90f, 0f)); // looks west

		spawnManager.spawnUnit(player, unit(null, runEastFrom(-40.5)), loc -> true);

		Location spot = ringSpot();
		assertTrue(spot.getX() > 0.5, "never in his view; the fallback ring ahead (east): " + spot);
	}

	@Test
	@DisplayName("T-423: Spawn_Between 0 keeps the 0.16 ring ahead of the heading")
	void handoffUnit_spawnBetweenOff_keepsTheRingAhead() {
		when(spawnConfig.getHandoffSettings()).thenReturn(new HandoffSettings(true, 2, 10, 60.0, 24.0, 0.0));
		blocked = dir -> true;

		spawnManager.spawnUnit(player, unit(null, runEastFrom(-40.5)), loc -> true);

		assertTrue(angle(new Vector(1, 0, 0), ringSpot()) <= 60.0, "ahead (east)");
	}

	/** Solid below {@link #GROUND_Y}, air at and above it. */
	private static Block block(int y) {
		Block   block = mock(Block.class);
		boolean solid = y < GROUND_Y;
		when(block.getType()).thenReturn(solid ? Material.STONE : Material.AIR);
		when(block.isEmpty()).thenReturn(!solid);
		when(block.isPassable()).thenReturn(!solid);
		return block;
	}
}
