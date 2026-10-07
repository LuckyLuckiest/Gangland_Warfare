package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawner;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Docket T-180 (P1, data loss): a spawner whose world is not loaded is skipped by {@code doLoadAll}, so the id counter
 * used to restart below it and the next created spawner overwrote that row. The repository now records the highest id
 * of EVERY stored row and the manager raises its counter to it.
 */
@DisplayName("CopSpawnerRepository: the id floor covers spawners of unloaded worlds (T-180)")
class CopSpawnerRepositoryTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private BukkitStatics         bukkit;
	private SqliteBackend         backend;
	private CopSpawnerRepository  repository;
	private World                 world;
	private World                 gone;

	@BeforeEach
	void setUp() throws SQLException {
		bukkit = BukkitStatics.install();
		world  = world("world");
		gone   = world("gone");
		bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("spawner.db")));
		backend.applySchema(TableSchemas.fromTable(new CopSpawnerTable()));
		repository = new CopSpawnerRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);

		repository.save(new CopSpawner(1, new Location(world, 1, 64, 1)));
		repository.save(new CopSpawner(2, new Location(gone, 2, 64, 2)));
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

	private CopSpawnManager manager() {
		CopLoader loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(mock(CopConfigProvider.class));
		return new CopSpawnManager(mock(JavaPlugin.class), loader, mock(NpcMarkManager.class),
		                           mock(BartizanNpcWeapons.class), mock(DownedTargetFilter.class), repository,
		                           mock(DetainmentService.class), mock(CuffLockRegistry.class));
	}

	/** Every row now in the table, the 'gone' world loaded so none is skipped. */
	private Map<Integer, String> rowsWithGoneWorldLoaded() {
		bukkit.statics().when(() -> Bukkit.getWorld("gone")).thenReturn(gone);
		try {
			return repository.loadAll().stream()
			                 .collect(Collectors.toMap(CopSpawner::getId, s -> s.getLocation().getWorld().getName(),
			                                           (a, b) -> a));
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
		CopSpawnManager manager = manager();
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
