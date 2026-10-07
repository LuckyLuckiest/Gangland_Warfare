package org.luckyraven.gangland.copsncrooks.place;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.database.SetupPointRepository;
import org.luckyraven.gangland.copsncrooks.database.SetupPointTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The setup-point registry over a real SQLite {@code cop_point} table. */
@DisplayName("SetupPointRegistry: setup points persist by kind")
class SetupPointRegistryTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private SqliteBackend       backend;
	private SetupPointRepository repository;
	private SetupPointRegistry  registry;
	private World               world;

	@BeforeEach
	void setUp() throws SQLException {
		world = mock(World.class);
		when(world.getName()).thenReturn("world");

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("point.db")));
		backend.applySchema(TableSchemas.fromTable(new SetupPointTable()));
		repository = new SetupPointRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);
		registry   = new SetupPointRegistry(repository);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	private SetupPointRegistry reloaded() {
		SetupPointRegistry fresh = new SetupPointRegistry(repository);
		fresh.onInitialize(true);
		return fresh;
	}

	@Test
	@DisplayName("ofKind returns only that kind, by id")
	void ofKind_filters() {
		registry.create(SetupPoint.PICKUP, "Dock", new Location(world, 1, 64, 1));
		registry.create(SetupPoint.BREAKER_TRIGGER, "Gate", new Location(world, 2, 64, 2));
		registry.create(SetupPoint.PICKUP, "Roof", new Location(world, 3, 64, 3));

		assertEquals(List.of("Dock", "Roof"),
		             registry.ofKind(SetupPoint.PICKUP).stream().map(SetupPoint::getName).toList());
		assertEquals(1, registry.ofKind(SetupPoint.BREAKER_TRIGGER).size());
		assertTrue(registry.ofKind("other").isEmpty());
	}

	@Test
	@DisplayName("a reload round-trips every column and continues the ids")
	void reload_roundTrip() {
		registry.create(SetupPoint.PICKUP, "Dock", new Location(world, 1.5, 64, -2.5));

		SetupPointRegistry fresh = reloaded();

		SetupPoint point = fresh.get(1);
		assertEquals(SetupPoint.PICKUP, point.getKind());
		assertEquals("Dock", point.getName());
		assertEquals("world", point.getWorld());
		assertEquals(1.5, point.getX());
		assertEquals(-2.5, point.getZ());
		assertEquals(2, fresh.create(SetupPoint.PICKUP, "Roof", new Location(world, 0, 0, 0)).getId());
	}

	@Test
	@DisplayName("remove deletes the row")
	void remove_deletesRow() {
		registry.create(SetupPoint.PICKUP, "Dock", new Location(world, 1, 64, 1));

		assertTrue(registry.remove(1));
		assertFalse(registry.remove(1));

		assertNull(registry.get(1));
		assertTrue(reloaded().all().isEmpty());
	}
}
