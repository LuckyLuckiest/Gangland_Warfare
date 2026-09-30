package org.luckyraven.gangland.copsncrooks.npc.police.spawn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpcFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H13 (B2): with the only spawner in range avoided (a cop recycled from it), a replacement comes from the ring
 * around the suspect. The ring only takes spots with the suspect's own indoor/outdoor state, so a suspect indoors on
 * street level found no spot at all and no replacement ever came; the fallback then accepts the street outside.
 * {@code spawnNearPlayer} passes its filter to the spawner search, so a rejected spawner hands over to that ring.
 */
@DisplayName("CopSpawnManager - ring fallback for a suspect indoors")
class CopSpawnManagerFallbackTest {

	private static final int GROUND_Y = 64;

	private World           world;
	private Player          player;
	private CopSpawnManager spawnManager;
	/** The Y the world reports as highest everywhere but the suspect's own column. */
	private int             roofElsewhere;

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
		when(config.getSpawnPhase1Attempts()).thenReturn(5);
		when(config.getSpawnPhase2Attempts()).thenReturn(5);
		CopLoader loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(config);

		@SuppressWarnings("unchecked") IRepository<CopSpawner> repository = mock(IRepository.class);
		spawnManager = new CopSpawnManager(mock(JavaPlugin.class), loader, mock(NpcMarkManager.class),
		                                   mock(BartizanNpcWeapons.class), mock(DownedTargetFilter.class), repository,
		                                   mock(DetainmentService.class), mock(CuffLockRegistry.class));

		world = mock(World.class);
		when(world.getMaxHeight()).thenReturn(320);
		when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
		when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> block(inv.getArgument(1)));
		when(world.getHighestBlockYAt(anyInt(), anyInt())).thenAnswer(inv -> {
			int x = inv.getArgument(0);
			int z = inv.getArgument(1);
			return x == 0 && z == 0 ? GROUND_Y + 6 : roofElsewhere;
		});

		player = mock(Player.class);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 0.5, GROUND_Y, 0.5));
	}

	@Test
	@DisplayName("a suspect under a roof with only open street around him gets a street spot at his level")
	void indoorSuspect_streetAround_getsStreetSpot() {
		roofElsewhere = GROUND_Y - 1; // open sky everywhere else

		Location spot = spawnManager.findRingLocation(player);
		assertNotNull(spot);
		assertTrue(Math.abs(spot.getY() - GROUND_Y) <= 4.0);
	}

	@Test
	@DisplayName("a suspect in the open is never given a spot under a roof by the fallback")
	void outdoorSuspect_onlyRoofedAround_getsNothing() {
		when(world.getHighestBlockYAt(anyInt(), anyInt())).thenAnswer(inv -> {
			int x = inv.getArgument(0);
			int z = inv.getArgument(1);
			return x == 0 && z == 0 ? GROUND_Y - 1 : GROUND_Y + 6;
		});

		assertNull(spawnManager.findRingLocation(player));
	}

	@Test
	@DisplayName("with the only in-range spawner avoided, spawnNearPlayer takes the ring; allowed, the spawner")
	void spawnNearPlayer_avoidedSpawner_fallsBackToRing() {
		roofElsewhere = GROUND_Y - 1;
		CopNpcFactory factory = mock(CopNpcFactory.class);
		spawnManager.copNpcFactory = factory;
		Location spawner = new Location(world, 5.5, GROUND_Y + 10, 5.5); // within Spawner_Max_Y_Diff 16
		spawnManager.setSpawnerLocation(spawner);

		spawnManager.spawnNearPlayer(player, 2, loc -> false);

		ArgumentCaptor<Location> ring = ArgumentCaptor.forClass(Location.class);
		verify(factory).createCop(ring.capture(), eq(2), eq(true));
		verify(factory, never()).createCop(any(), anyInt());
		assertTrue(ring.getValue().distance(spawner) > 3, "the ring spot, not the avoided spawner");

		spawnManager.spawnNearPlayer(player, 2, loc -> true);

		verify(factory).createCop(spawner, 2);
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
