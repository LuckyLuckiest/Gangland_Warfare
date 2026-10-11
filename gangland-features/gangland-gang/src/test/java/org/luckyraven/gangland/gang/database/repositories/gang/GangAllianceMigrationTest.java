package org.luckyraven.gangland.gang.database.repositories.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.persistence.database.Database;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T-712: the diff engine adds {@code friendly_fire} to a legacy {@code gang_ally} table before
 * {@code migrateSchema} rebuilds it; the rebuild must carry the column instead of dropping it.
 */
@DisplayName("GangAllianceRepository - legacy rebuild keeps friendly_fire")
class GangAllianceMigrationTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private Connection    conn;
	private SqliteBackend backend;

	@BeforeEach
	void setUp() throws SQLException {
		Path dbFile = tempDir.resolve("gang-ally.db");
		conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(dbFile));
	}

	@AfterEach
	void tearDown() throws SQLException {
		backend.disconnect();
		conn.close();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("friendly_fire survives the composite-key rebuild and no _prerebuild_ backup appears")
	void migrateSchema_keepsFriendlyFire() throws SQLException {
		try (Statement stmt = conn.createStatement()) {
			stmt.execute("CREATE TABLE gang (id INTEGER PRIMARY KEY)");
			stmt.execute("CREATE TABLE gang_ally (gang_id INTEGER PRIMARY KEY NOT NULL, ally_id INTEGER NOT NULL " +
			             "UNIQUE, since INTEGER DEFAULT -1)");
			stmt.execute("INSERT INTO gang (id) VALUES (1)");
			stmt.execute("INSERT INTO gang (id) VALUES (2)");
			// what applySchema does before migrateSchema runs
			stmt.execute("ALTER TABLE gang_ally ADD COLUMN friendly_fire BOOLEAN DEFAULT 0");
			stmt.execute("INSERT INTO gang_ally (gang_id, ally_id, since, friendly_fire) VALUES (1, 2, 5, 1)");
		}

		Database database = mock(Database.class);
		when(database.getConnection()).thenReturn(conn);
		DatabaseHandler handler = mock(DatabaseHandler.class);
		when(handler.getType()).thenReturn(DatabaseHandler.SQLITE);
		when(handler.getDatabase()).thenReturn(database);
		JavaPlugin plugin = PluginMocks.plugin(tempDir);

		new GangAllianceRepository(plugin, handler, backend).migrateSchema();

		try (Statement stmt = conn.createStatement()) {
			ResultSet rs = stmt.executeQuery("SELECT friendly_fire FROM gang_ally WHERE gang_id = 1");
			rs.next();
			assertEquals(1, rs.getInt(1));
			rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE name LIKE '%prerebuild%'");
			assertFalse(rs.next());
		}
	}
}
