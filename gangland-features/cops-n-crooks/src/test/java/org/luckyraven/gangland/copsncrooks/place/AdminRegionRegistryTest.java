package org.luckyraven.gangland.copsncrooks.place;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.database.AdminRegionRepository;
import org.luckyraven.gangland.copsncrooks.database.AdminRegionTable;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The admin-region registry over a real SQLite {@code cop_region} table, and as a {@code PlaceNames} provider. */
@DisplayName("AdminRegionRegistry: admin cuboids persist and answer through PlaceNames")
class AdminRegionRegistryTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private SqliteBackend         backend;
	private AdminRegionRepository repository;
	private AdminRegionRegistry   registry;
	private World                 world;

	@BeforeEach
	void setUp() throws SQLException {
		world = world("world");

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("region.db")));
		backend.applySchema(TableSchemas.fromTable(new AdminRegionTable()));
		repository = new AdminRegionRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);
		registry   = new AdminRegionRegistry(repository);
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

	private AdminRegionRegistry reloaded() {
		AdminRegionRegistry fresh = new AdminRegionRegistry(repository);
		fresh.onInitialize(true);
		return fresh;
	}

	@Test
	@DisplayName("create normalises the corners and the tag")
	void create_normalises() {
		AdminRegion region = registry.create("Old Town", new Location(world, 10, 70, 10), new Location(world, -5, 60, 0),
		                                     "  District ");

		assertEquals(1, region.getId());
		assertEquals(-5, region.getMinX());
		assertEquals(60, region.getMinY());
		assertEquals(0, region.getMinZ());
		assertEquals(10, region.getMaxX());
		assertEquals(70, region.getMaxY());
		assertEquals(10, region.getMaxZ());
		assertEquals(Set.of("district"), region.getTags());
	}

	@Test
	@DisplayName("two worlds are rejected and nothing is stored")
	void create_twoWorlds_throws() {
		assertThrows(IllegalArgumentException.class,
		             () -> registry.create("Bad", new Location(world, 0, 0, 0), new Location(world("nether"), 5, 5, 5),
		                                   "district"));

		assertTrue(registry.all().isEmpty());
		assertTrue(reloaded().all().isEmpty());
	}

	@Test
	@DisplayName("regionsAt returns the PlaceRegion with id copsncrooks:<id>, the tag and no owner")
	void regionsAt_returnsPlaceRegion() {
		registry.create("Old Town", new Location(world, 0, 60, 0), new Location(world, 10, 70, 10), "district");

		List<PlaceRegion> at = registry.regionsAt(new Location(world, 5.5, 64, 5.5));

		assertEquals(1, at.size());
		PlaceRegion place = at.get(0);
		assertEquals("copsncrooks:1", place.id());
		assertEquals("Old Town", place.name());
		assertEquals(PlaceRegion.NO_OWNER, place.ownerGangId());
		assertTrue(place.hasTag(PlaceRegion.TAG_DISTRICT));
		assertTrue(registry.regionsAt(new Location(world, 50, 64, 5)).isEmpty());
		assertTrue(registry.regionsAt(new Location(world("nether"), 5, 64, 5)).isEmpty());
	}

	@Test
	@DisplayName("a registered registry answers PlaceNames, smaller region first")
	void placeNames_answersThroughTheProvider() {
		PlaceNames names = new PlaceNames();
		names.register(registry);
		registry.create("Big", new Location(world, 0, 0, 0), new Location(world, 100, 100, 100), "district");
		registry.create("Small", new Location(world, 4, 0, 4), new Location(world, 6, 100, 6), "hideout");

		Location at = new Location(world, 5, 64, 5);

		assertEquals("Small", names.locate(at).orElseThrow());
		assertEquals("Big", names.withTag(at, PlaceRegion.TAG_DISTRICT).orElseThrow().name());
		assertEquals("copsncrooks", registry.source());
	}

	@Test
	@DisplayName("a reload round-trips every column, tags included")
	void reload_roundTrip() {
		registry.create("Old Town", new Location(world, 0, 60, 0), new Location(world, 10, 70, 10), "district");
		registry.create("Plain", new Location(world, 1, 1, 1), new Location(world, 2, 2, 2), "");

		AdminRegionRegistry fresh = reloaded();

		AdminRegion town = fresh.get(1);
		assertEquals("world", town.getWorld());
		assertEquals(70, town.getMaxY());
		assertEquals(Set.of("district"), town.getTags());
		assertTrue(fresh.get(2).getTags().isEmpty());
		assertEquals(3, fresh.create("Next", new Location(world, 0, 0, 0), new Location(world, 1, 1, 1), "x").getId());
	}

	@Test
	@DisplayName("withTag finds regions by lowercase tag, and remove deletes the row")
	void withTag_andRemove() {
		registry.create("A", new Location(world, 0, 0, 0), new Location(world, 1, 1, 1), "hideout");
		registry.create("B", new Location(world, 0, 0, 0), new Location(world, 1, 1, 1), "district");

		assertEquals(List.of("A"), registry.withTag("HIDEOUT").stream().map(AdminRegion::getName).toList());

		assertTrue(registry.remove(1));
		assertFalse(registry.remove(1));
		assertEquals(1, reloaded().all().size());
	}
}
