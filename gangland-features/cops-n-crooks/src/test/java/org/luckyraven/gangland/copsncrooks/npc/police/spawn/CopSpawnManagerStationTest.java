package org.luckyraven.gangland.copsncrooks.npc.police.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spawner grouping by station: assignNearby, a spawner of another station is skipped, spawnersOf, unassign. */
@DisplayName("CopSpawnManager: stations group spawners")
class CopSpawnManagerStationTest {

	private BukkitStatics            bukkit;
	private World                    world;
	private World                    nether;
	private IRepository<CopSpawner>  repository;
	private CopSpawnManager          manager;
	private Station                  central;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		world  = world("world");
		nether = world("nether");
		bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

		CopLoader loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(mock(CopConfigProvider.class));
		repository = mock(IRepository.class);
		manager = new CopSpawnManager(mock(JavaPlugin.class), loader, mock(NpcMarkManager.class),
		                              mock(BartizanNpcWeapons.class), mock(DownedTargetFilter.class), repository,
		                              mock(DetainmentService.class), mock(CuffLockRegistry.class));
		central = new Station(1, "Central", "world", 0, 64, 0, 0f, null);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private static World world(String name) {
		World w = mock(World.class);
		when(w.getName()).thenReturn(name);
		return w;
	}

	@Test
	@DisplayName("assignNearby takes every unassigned spawner within the radius, same world, horizontally")
	void assignNearby_takesTheOnesInRadius() {
		manager.setSpawnerLocation(new Location(world, 10, 64, 0));      // 1: in
		manager.setSpawnerLocation(new Location(world, 31, 200, 0));     // 2: in (height ignored)
		manager.setSpawnerLocation(new Location(world, 33, 64, 0));      // 3: out
		manager.setSpawnerLocation(new Location(nether, 5, 64, 0));      // 4: other world

		int joined = manager.assignNearby(central, 32.0);

		assertEquals(2, joined);
		assertEquals(List.of(1, 2), manager.spawnersOf(1).stream().map(CopSpawner::getId).sorted().toList());
		assertNull(spawner(3).getStationId());
		assertNull(spawner(4).getStationId());
	}

	@Test
	@DisplayName("a spawner already in another station is skipped, and each assignment is persisted")
	void assignNearby_skipsAssignedSpawner() {
		manager.setSpawnerLocation(new Location(world, 10, 64, 0));
		manager.setSpawnerLocation(new Location(world, 12, 64, 0));
		manager.assignStation(1, 9);

		assertEquals(1, manager.assignNearby(central, 32.0));

		assertEquals(9, spawner(1).getStationId());
		assertEquals(1, spawner(2).getStationId());
		verify(repository, times(2)).save(spawner(1));   // set, then assignStation(9)
	}

	@Test
	@DisplayName("a station whose world is not loaded joins nothing")
	void assignNearby_unloadedWorld_joinsNothing() {
		manager.setSpawnerLocation(new Location(world, 1, 64, 1));

		assertEquals(0, manager.assignNearby(new Station(2, "Gone", "gone", 0, 64, 0, 0f, null), 32.0));
	}

	@Test
	@DisplayName("unassign frees only that station's spawners")
	void unassignStation_freesItsSpawners() {
		manager.setSpawnerLocation(new Location(world, 1, 64, 1));
		manager.setSpawnerLocation(new Location(world, 2, 64, 2));
		manager.assignStation(1, 1);
		manager.assignStation(2, 2);

		manager.unassignStation(1);

		assertNull(spawner(1).getStationId());
		assertEquals(2, spawner(2).getStationId());
		assertEquals(List.of(2), manager.spawnersOf(2).stream().map(CopSpawner::getId).toList());
		assertEquals(List.of(), manager.spawnersOf(1));
	}

	private CopSpawner spawner(int id) {
		return manager.getSpawners().stream().filter(s -> s.getId() == id).findFirst().orElseThrow();
	}
}
