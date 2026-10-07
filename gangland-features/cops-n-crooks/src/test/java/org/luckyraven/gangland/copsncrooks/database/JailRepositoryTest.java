package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Docket T-180 (P1, data loss), jails: {@code JailService.ID} was set inside the load loop after the unloaded-world
 * skip (and to the last row, not the maximum), so a jail of an unloaded world lost its id to the next created jail.
 */
@DisplayName("JailRepository: the id floor covers jails of unloaded worlds (T-180)")
class JailRepositoryTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private BukkitStatics  bukkit;
	private SqliteBackend  backend;
	private JailRepository repository;
	private World          world;
	private World          gone;

	@BeforeEach
	void setUp() throws SQLException {
		JailService.ID = 0;
		bukkit = BukkitStatics.install();
		world  = world("world");
		gone   = world("gone");
		bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("jail.db")));
		backend.applySchema(TableSchemas.fromTable(new JailTable()));
		repository = new JailRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);

		repository.save(new Jail(1, new Location(world, 1, 64, 1), 4));
		repository.save(new Jail(2, new Location(gone, 2, 64, 2), 4));
	}

	@AfterEach
	void tearDown() {
		JailService.ID = 0;
		backend.disconnect();
		bukkit.close();
		DbFiles.release(tempDir);
	}

	private static World world(String name) {
		World w = mock(World.class);
		when(w.getName()).thenReturn(name);
		return w;
	}

	private Map<Integer, String> rowsWithGoneWorldLoaded() {
		bukkit.statics().when(() -> Bukkit.getWorld("gone")).thenReturn(gone);
		try {
			return repository.loadAll().stream()
			                 .collect(Collectors.toMap(Jail::getId, j -> j.getLocation().getWorld().getName(),
			                                           (a, b) -> a));
		} finally {
			bukkit.statics().when(() -> Bukkit.getWorld("gone")).thenReturn(null);
		}
	}

	@Test
	@DisplayName("reloadWithAnUnloadedWorld_newRowDoesNotReuseItsId")
	void reloadWithAnUnloadedWorld_newRowDoesNotReuseItsId() {
		JailService service = new JailService(new JailRegistry(), repository);
		service.onInitialize(true);

		Jail created = service.setJailLocation(new Location(world, 3, 64, 3), 4);

		assertEquals(3, created.getId(), "the new jail takes id 3, not the unloaded world's 2");
		Map<Integer, String> rows = rowsWithGoneWorldLoaded();
		assertEquals("gone", rows.get(2), "row 2 of the unloaded world is untouched");
		assertEquals("world", rows.get(3));
	}
}
