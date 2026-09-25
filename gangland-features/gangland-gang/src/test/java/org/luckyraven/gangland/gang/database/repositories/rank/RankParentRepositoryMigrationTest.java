package org.luckyraven.gangland.gang.database.repositories.rank;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.gang.database.tables.rank.RankParentTable;
import org.luckyraven.gangland.gang.database.tables.rank.RankTable;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankParent;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.SchemaMigrations;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.persistence.database.Database;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GR-04: {@code rank_parent} declared {@code id} as its sole primary key, so a rank could persist only one link
 * and saving a second one (a branching hierarchy) overwrote the first. {@link RankParentRepository#migrateSchema()}
 * flips deployed tables to a composite key on {@code (id, parent_id)}, keeping every row.
 */
@DisplayName("RankParentRepository - composite key migration and link deletes")
class RankParentRepositoryMigrationTest {

	private static final String LEGACY_DDL = "CREATE TABLE rank_parent (id INTEGER PRIMARY KEY NOT NULL, " +
	                                         "parent_id INTEGER NOT NULL UNIQUE, " +
	                                         "FOREIGN KEY (parent_id) REFERENCES rank_tree(id))";

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: SQLite holds the .db handle past the test
	Path tempDir;

	private Connection           legacyConnection;
	private SqliteBackend        backend;
	private RankParentRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		Path dbFile = tempDir.resolve("rank-parent.db");
		legacyConnection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(dbFile));
		backend.applySchema(TableSchemas.fromTable(new RankTable()));

		Database database = mock(Database.class);
		when(database.getConnection()).thenReturn(legacyConnection);
		DatabaseHandler handler = mock(DatabaseHandler.class);
		when(handler.getType()).thenReturn(DatabaseHandler.SQLITE);
		when(handler.getDatabase()).thenReturn(database);

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		RankRepository ranks = new RankRepository(plugin, handler, backend);
		for (int id = 1; id <= 4; id++) ranks.save(new Rank("rank" + id, id));

		repository = new RankParentRepository(plugin, handler, backend);
	}

	@AfterEach
	void tearDown() throws SQLException {
		backend.disconnect();
		if (legacyConnection != null) legacyConnection.close();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("a migrated legacy table keeps its rows and holds two links for one rank")
	void migrateSchema_legacyTable_holdsABranchingRank() throws SQLException {
		try (Statement stmt = legacyConnection.createStatement()) {
			stmt.execute(LEGACY_DDL);
			stmt.execute("INSERT INTO rank_parent (id, parent_id) VALUES (1, 2)");
		}

		repository.migrateSchema();
		repository.migrateSchema(); // idempotent on every later startup

		assertTrue(SchemaMigrations.isColumnInPrimaryKey(legacyConnection, DatabaseHandler.SQLITE, "rank_parent",
		                                                 "parent_id"));

		repository.save(new RankParent(1, 3));

		assertEquals(Set.of(new RankParent(1, 2), new RankParent(1, 3)), new HashSet<>(repository.loadAll()));
	}

	@Test
	@DisplayName("delete removes one link; deleteAllForRank removes every link naming the rank on either side")
	void deletes_targetTheRightRows() throws SQLException {
		backend.applySchema(TableSchemas.fromTable(new RankParentTable(new RankTable())));
		repository.save(new RankParent(1, 2));
		repository.save(new RankParent(1, 3));
		repository.save(new RankParent(2, 4));

		repository.delete(new RankParent(1, 3));
		assertEquals(Set.of(new RankParent(1, 2), new RankParent(2, 4)), new HashSet<>(repository.loadAll()));

		repository.deleteAllForRank(2);
		assertTrue(repository.loadAll().isEmpty());
	}

}
