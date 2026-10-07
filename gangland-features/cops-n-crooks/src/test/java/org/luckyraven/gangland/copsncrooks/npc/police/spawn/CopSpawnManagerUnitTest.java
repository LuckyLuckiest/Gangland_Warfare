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
				.thenAnswer(inv -> blocked.test(inv.getArgument(1)) ? mock(RayTraceResult.class) : null);

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

	@Test
	@DisplayName("nothing out of sight: the 0.15 spawnNearPlayer path (the nearest spawner, even in view)")
	void nothingHidden_legacyPath() {
		Location spawner = new Location(world, 5.5, GROUND_Y, 5.5);
		spawnManager.setSpawnerLocation(spawner); // unassigned
		blocked = dir -> false;

		CopNpc cop = spawnManager.spawnUnit(player, unit(null, null), loc -> true);

		assertNotNull(cop);
		verify(factory).createCop(spawner, 3, false, MARKSMAN);
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
		blocked = dir -> true;
		SpawnBias bias = new SpawnBias(new Vector(1, 0, 0), new Location(world, 0.5, GROUND_Y, 0.5), 0L, 60.0);

		spawnManager.spawnUnit(player, unit(null, bias), loc -> true);

		Location spot = ringSpot();
		assertTrue(angle(new Vector(1, 0, 0), spot) <= 60.0);
		assertSame(world, spot.getWorld());
	}

	/** Solid below {@link #GROUND_Y}, air at and above it. */
	private static Block block(int y) {
		Block   block = mock(Block.class);
		boolean solid = y < GROUND_Y;
		when(block.getType()).thenReturn(solid ? Material.STONE : Material.AIR);
		when(block.isEmpty()).thenReturn(!solid);
		return block;
	}
}
