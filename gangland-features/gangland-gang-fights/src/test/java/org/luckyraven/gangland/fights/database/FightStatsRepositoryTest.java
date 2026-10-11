package org.luckyraven.gangland.fights.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("fight repositories: real SQLite through the DatabaseBackend SPI")
class FightStatsRepositoryTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private SqliteBackend backend;

	@BeforeEach
	void setUp() throws SQLException {
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("fights.db")));
		backend.applySchema(TableSchemas.fromTable(new FightStatsTable()));
		backend.applySchema(TableSchemas.fromTable(new FightPlayerStatsTable()));
		backend.applySchema(TableSchemas.fromTable(new FightRecordTable()));
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("roundTrip: gang stats, player stats and a fight record keep every column")
	void roundTrip() {
		var plugin = PluginMocks.plugin(tempDir);
		var db     = mock(DatabaseHandler.class);

		FightStatsRepository       stats   = new FightStatsRepository(plugin, db, backend);
		FightPlayerStatsRepository players = new FightPlayerStatsRepository(plugin, db, backend);
		FightRecordRepository      records = new FightRecordRepository(plugin, db, backend);

		UUID player = UUID.randomUUID();
		stats.save(new FightStats(4, 5, 2, 1));
		players.save(new FightPlayerStats(player, 3, 1, 9));
		records.save(new FightRecord(7L, 4, 8, 4, 1500.5, true, 123456789L));

		assertEquals(new FightStats(4, 5, 2, 1), stats.loadAll().iterator().next());
		assertEquals(new FightPlayerStats(player, 3, 1, 9), players.loadAll().iterator().next());
		assertEquals(new FightRecord(7L, 4, 8, 4, 1500.5, true, 123456789L), records.loadAll().iterator().next());
	}

	@Test
	@DisplayName("saving the same gang twice updates its row; delete removes it")
	void upsertAndDelete() {
		FightStatsRepository stats = new FightStatsRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class),
		                                                      backend);

		stats.save(new FightStats(4, 1, 0, 0));
		stats.save(new FightStats(4, 2, 0, 0));
		assertEquals(1, stats.loadAll().size());
		assertEquals(2, stats.loadAll().iterator().next().wins());

		stats.delete(new FightStats(4, 2, 0, 0));
		assertTrue(stats.loadAll().isEmpty());
	}
}
