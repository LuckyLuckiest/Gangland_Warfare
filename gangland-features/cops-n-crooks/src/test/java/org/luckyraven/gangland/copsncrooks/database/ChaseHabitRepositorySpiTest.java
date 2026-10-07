package org.luckyraven.gangland.copsncrooks.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseHabit;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("ChaseHabitRepository: real SQLite through the DatabaseBackend SPI")
class ChaseHabitRepositorySpiTest {

	private static final UUID RUNNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID OTHER  = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private SqliteBackend         backend;
	private ChaseHabitRepository  repository;

	@BeforeEach
	void setUp() throws SQLException {
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("habit.db")));
		backend.applySchema(TableSchemas.fromTable(new ChaseHabitTable()));

		repository = new ChaseHabitRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("a saved habit round-trips every column")
	void roundTrip() {
		repository.save(new ChaseHabit(RUNNER, 1.9, 1.0, 0.5, 123_456_789L));

		Collection<ChaseHabit> loaded = repository.loadAll();

		assertEquals(1, loaded.size());
		assertEquals(new ChaseHabit(RUNNER, 1.9, 1.0, 0.5, 123_456_789L), loaded.iterator().next());
	}

	@Test
	@DisplayName("saving the same player twice updates the row")
	void saveTwice_upserts() {
		repository.save(new ChaseHabit(RUNNER, 1, 1, 0.5, 1L));
		repository.save(new ChaseHabit(RUNNER, 2, 1, 1, 2L));

		Collection<ChaseHabit> loaded = repository.loadAll();

		assertEquals(1, loaded.size());
		assertEquals(2L, loaded.iterator().next().lastAt());
	}

	@Test
	@DisplayName("an empty table loads nothing")
	void emptyLoad() {
		assertTrue(repository.loadAll().isEmpty());
	}

	@Test
	@DisplayName("delete removes only that player's row")
	void delete_removesOneRow() {
		repository.save(new ChaseHabit(RUNNER, 1, 1, 1, 1L));
		repository.save(new ChaseHabit(OTHER, 1, 0, 1, 1L));

		repository.delete(new ChaseHabit(RUNNER, 1, 1, 1, 1L));

		Collection<ChaseHabit> loaded = repository.loadAll();
		assertEquals(1, loaded.size());
		assertEquals(OTHER, loaded.iterator().next().player());
	}
}
