package org.luckyraven.gangland.copsncrooks.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseLevelStat;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("ChaseLevelStatRepository: real SQLite through the DatabaseBackend SPI")
class ChaseLevelStatRepositorySpiTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private SqliteBackend            backend;
	private ChaseLevelStatRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("level.db")));
		backend.applySchema(TableSchemas.fromTable(new ChaseLevelStatTable()));

		repository = new ChaseLevelStatRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("a saved level row round-trips every column")
	void roundTrip() {
		ChaseLevelStat row = new ChaseLevelStat(3, 12.5, 7.25, 41.5, 9.5, 987_654_321L);
		repository.save(row);

		Collection<ChaseLevelStat> loaded = repository.loadAll();

		assertEquals(1, loaded.size());
		assertEquals(row, loaded.iterator().next());
	}

	@Test
	@DisplayName("saving the same level twice updates the row")
	void saveTwice_upserts() {
		repository.save(new ChaseLevelStat(2, 1, 1, 30, 1, 1L));
		repository.save(new ChaseLevelStat(2, 2, 1, 35, 2, 2L));

		Collection<ChaseLevelStat> loaded = repository.loadAll();

		assertEquals(1, loaded.size());
		assertEquals(2L, loaded.iterator().next().updatedAt());
	}

	@Test
	@DisplayName("an empty table loads nothing")
	void emptyLoad() {
		assertTrue(repository.loadAll().isEmpty());
	}

	@Test
	@DisplayName("delete removes only that level's row")
	void delete_removesOneRow() {
		repository.save(new ChaseLevelStat(2, 1, 1, 30, 1, 1L));
		repository.save(new ChaseLevelStat(3, 1, 0, 30, 1, 1L));

		repository.delete(new ChaseLevelStat(2, 1, 1, 30, 1, 1L));

		Collection<ChaseLevelStat> loaded = repository.loadAll();
		assertEquals(1, loaded.size());
		assertEquals(3, loaded.iterator().next().level());
	}
}
