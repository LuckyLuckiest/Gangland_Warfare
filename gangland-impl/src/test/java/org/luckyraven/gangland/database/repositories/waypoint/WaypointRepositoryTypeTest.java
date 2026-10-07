package org.luckyraven.gangland.database.repositories.waypoint;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.database.tables.waypoint.WaypointTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Ruling R35: a waypoint row whose type this version does not know (written by a newer one, or by a downgrade of this
 * very release that lost HOSPITAL) is skipped with a warning; every other row still loads. The new {@code hospital}
 * type round-trips like any other.
 */
@DisplayName("WaypointRepository - an unknown type skips one row, not the load")
class WaypointRepositoryTypeTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: SQLite holds the .db handle past the test
	Path tempDir;

	private BukkitStatics      bukkit;
	private SqliteBackend      backend;
	private WaypointRepository repository;
	private Path               db;

	@BeforeEach
	void setUp() throws SQLException {
		bukkit  = BukkitStatics.install();
		db      = tempDir.resolve("waypoint.db");
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(db));
		backend.applySchema(TableSchemas.fromTable(new WaypointTable()));

		repository = new WaypointRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		bukkit.close();
		DbFiles.release(tempDir);
	}

	private Waypoint waypoint(String name, Waypoint.WaypointType type) {
		Waypoint waypoint = new Waypoint(name, "gangland");
		waypoint.setType(type);
		waypoint.setCoordinates("world", 1, 2, 3, 0F, 0F);
		return waypoint;
	}

	private Map<String, Waypoint> loaded() {
		return repository.loadAll().stream().collect(Collectors.toMap(Waypoint::getName, Function.identity()));
	}

	@Test
	@DisplayName("a row with an unknown type is skipped and the others load")
	void unknownTypeRow_isSkipped_othersLoad() throws SQLException {
		repository.save(waypoint("kept", Waypoint.WaypointType.SPAWN));
		repository.save(waypoint("future", Waypoint.WaypointType.GLOBAL));
		repository.save(waypoint("also-kept", Waypoint.WaypointType.SAFE_ZONE));
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
		     Statement statement = connection.createStatement()) {
			statement.executeUpdate("UPDATE waypoint SET type = 'moonbase' WHERE name = 'future'");
		}

		Map<String, Waypoint> loaded = loaded();

		assertEquals(2, loaded.size());
		assertTrue(loaded.containsKey("kept"));
		assertTrue(loaded.containsKey("also-kept"));
		assertFalse(loaded.containsKey("future"));
	}

	/** Final fix round 1: the skipped row's id still floors the next new waypoint's, so it is never reused. */
	@Test
	@DisplayName("a skipped row still counts toward the highest stored id")
	void unknownTypeRow_countsTowardTheHighestStoredId() throws SQLException {
		Waypoint kept   = waypoint("kept", Waypoint.WaypointType.SPAWN);
		Waypoint future = waypoint("future", Waypoint.WaypointType.GLOBAL);
		kept.setUsedId(3);
		future.setUsedId(9);
		repository.save(kept);
		repository.save(future);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
		     Statement statement = connection.createStatement()) {
			statement.executeUpdate("UPDATE waypoint SET type = 'hospital_v2' WHERE name = 'future'");
		}

		assertEquals(1, repository.loadAll().size());
		assertEquals(9, repository.getHighestStoredId());
	}

	@Test
	@DisplayName("a hospital waypoint saves and loads back as a hospital")
	void hospital_loads() {
		repository.save(waypoint("ward", Waypoint.WaypointType.HOSPITAL));

		assertEquals(Waypoint.WaypointType.HOSPITAL, loaded().get("ward").getType());
	}

}
