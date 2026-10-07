package org.luckyraven.gangland.copsncrooks.station;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.database.StationRepository;
import org.luckyraven.gangland.copsncrooks.database.StationTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The police-station registry over a real SQLite {@code cop_station} table: ids, names, nearest, jail link, reload. */
@DisplayName("StationRegistry: stations persist and answer by name and distance")
class StationRegistryTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private SqliteBackend     backend;
	private StationRepository repository;
	private StationRegistry   registry;
	private World             world;
	private World             nether;

	@BeforeEach
	void setUp() throws SQLException {
		world  = world("world");
		nether = world("nether");

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("station.db")));
		backend.applySchema(TableSchemas.fromTable(new StationTable()));
		repository = new StationRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);
		registry   = new StationRegistry(repository);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	private static World world(String name) {
		World w = mock(World.class);
		when(w.getName()).thenReturn(name);
		return w;
	}

	private StationRegistry reloaded() {
		StationRegistry fresh = new StationRegistry(repository);
		fresh.onInitialize(true);
		return fresh;
	}

	@Test
	@DisplayName("create assigns max+1 and persists at once")
	void create_assignsNextIdAndPersists() {
		Station first  = registry.create("Central", new Location(world, 0, 64, 0, 90f, 0f));
		Station second = registry.create("Harbour", new Location(world, 100, 64, 0));

		assertEquals(1, first.getId());
		assertEquals(2, second.getId());
		StationRegistry fresh = reloaded();
		assertEquals(2, fresh.all().size());
		assertEquals(90f, fresh.get(1).getYaw());
		assertEquals("world", fresh.get(1).getWorld());
	}

	@Test
	@DisplayName("a duplicate name (any case) returns null and stores nothing")
	void create_duplicateName_isNull() {
		registry.create("Central", new Location(world, 0, 64, 0));

		assertNull(registry.create("CENTRAL", new Location(world, 5, 64, 5)));

		assertEquals(1, registry.all().size());
		assertEquals(1, reloaded().all().size());
	}

	@Test
	@DisplayName("remove drops the station from memory and from the table")
	void remove_deletesRow() {
		registry.create("Central", new Location(world, 0, 64, 0));
		registry.create("Harbour", new Location(world, 100, 64, 0));

		assertTrue(registry.remove(1));
		assertFalse(registry.remove(1));

		assertNull(registry.get(1));
		StationRegistry fresh = reloaded();
		assertEquals(1, fresh.all().size());
		assertEquals("Harbour", fresh.all().get(0).getName());
	}

	@Test
	@DisplayName("byName is case-insensitive")
	void byName_ignoresCase() {
		Station central = registry.create("Central", new Location(world, 0, 64, 0));

		assertSame(central, registry.byName("cEnTrAl"));
		assertNull(registry.byName("Nowhere"));
	}

	@Test
	@DisplayName("nearest looks only at the same world, by horizontal distance, at any distance")
	void nearest_sameWorldOnly() {
		Station central = registry.create("Central", new Location(world, 0, 64, 0));
		Station harbour = registry.create("Harbour", new Location(world, 100, 200, 0));
		Station hell    = registry.create("Hell", new Location(nether, 90, 64, 0));

		assertSame(harbour, registry.nearest(new Location(world, 90, 0, 0)));
		assertSame(central, registry.nearest(new Location(world, 10, 64, 10)));
		assertSame(hell, registry.nearest(new Location(nether, 0, 64, 0)));
		assertSame(harbour, registry.nearest(new Location(world, 100000, 64, 0)));
		assertNull(registry.nearest(new Location(world("end"), 0, 64, 0)));
	}

	@Test
	@DisplayName("linkJail persists the link, and null clears it")
	void linkJail_persists() {
		registry.create("Central", new Location(world, 0, 64, 0));

		registry.linkJail(1, 7);
		assertEquals(7, reloaded().get(1).getJailId());

		registry.linkJail(1, null);
		assertNull(reloaded().get(1).getJailId());
	}

	@Test
	@DisplayName("a reload keeps every station, and the next id continues after the highest")
	void reload_roundTripKeepsIds() {
		registry.create("Central", new Location(world, 0, 64, 0));
		registry.create("Harbour", new Location(world, 100, 64, 0));
		registry.linkJail(2, 3);

		StationRegistry fresh = reloaded();

		assertEquals(3, fresh.get(2).getJailId());
		assertEquals(3, fresh.create("Airport", new Location(world, 5, 64, 5)).getId());
	}
}
