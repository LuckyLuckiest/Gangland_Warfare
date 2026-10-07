package org.luckyraven.gangland.civilians.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.civilians.npc.config.CiviliansConfig;
import org.luckyraven.gangland.civilians.npc.config.CiviliansLoader;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpcFactory;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawner;
import org.luckyraven.keystone.npc.entity.SpawnConfigProvider;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Docket T-180 (P1, data loss), civilians half: a spawner of an unloaded world is skipped on load, so the id counter
 * restarted below it and the next created spawner overwrote that row. The id floor now covers every stored row.
 */
@DisplayName("CivilianSpawnerRepository: the id floor covers spawners of unloaded worlds (T-180)")
class CivilianSpawnerRepositoryTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private BukkitStatics             bukkit;
	private SqliteBackend             backend;
	private CivilianSpawnerRepository repository;
	private World                     world;
	private World                     gone;

	@BeforeEach
	void setUp() throws SQLException {
		bukkit = BukkitStatics.install();
		world  = world("world");
		gone   = world("gone");
		bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("civ.db")));
		backend.applySchema(TableSchemas.fromTable(new CivilianSpawnerTable()));
		repository = new CivilianSpawnerRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);

		repository.save(new CivilianSpawner(1, new Location(world, 1, 64, 1), null, null));
		repository.save(new CivilianSpawner(2, new Location(gone, 2, 64, 2), null, null));
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		bukkit.close();
		DbFiles.release(tempDir);
	}

	private static World world(String name) {
		World w = mock(World.class);
		when(w.getName()).thenReturn(name);
		return w;
	}

	private CivilianSpawnManager manager() {
		CiviliansLoader loader = mock(CiviliansLoader.class);
		when(loader.getLoadedConfig()).thenReturn(mock(CiviliansConfig.class));
		return new CivilianSpawnManager(mock(SpawnConfigProvider.class), repository, mock(CivilianNpcFactory.class),
		                                mock(CivilianNpcRegistry.class), loader);
	}

	private Map<Integer, String> rowsWithGoneWorldLoaded() {
		bukkit.statics().when(() -> Bukkit.getWorld("gone")).thenReturn(gone);
		try {
			return repository.loadAll().stream()
			                 .collect(Collectors.toMap(CivilianSpawner::getId,
			                                           s -> s.getLocation().getWorld().getName(), (a, b) -> a));
		} finally {
			bukkit.statics().when(() -> Bukkit.getWorld("gone")).thenReturn(null);
		}
	}

	@Test
	@DisplayName("the loader records the highest id over every row, loaded or not")
	void loadAll_recordsHighestStoredId() {
		repository.loadAll();

		assertEquals(2, repository.getHighestStoredId());
	}

	@Test
	@DisplayName("reloadWithAnUnloadedWorld_newRowDoesNotReuseItsId: initialise, then reload")
	void reloadWithAnUnloadedWorld_newRowDoesNotReuseItsId() {
		CivilianSpawnManager manager = manager();
		manager.onInitialize(true);

		manager.setSpawnerLocation(new Location(world, 3, 64, 3));
		assertTrue(manager.getSpawnerIds().contains(3), "the new spawner takes id 3, not the unloaded world's 2");
		Map<Integer, String> rows = rowsWithGoneWorldLoaded();
		assertEquals("gone", rows.get(2), "row 2 of the unloaded world is untouched");
		assertEquals("world", rows.get(3));

		manager.reloadSpawners();
		manager.setSpawnerLocation(new Location(world, 4, 64, 4));
		assertTrue(manager.getSpawnerIds().contains(4), "after a reload the floor still holds");
		assertEquals("gone", rowsWithGoneWorldLoaded().get(2));
	}
}
