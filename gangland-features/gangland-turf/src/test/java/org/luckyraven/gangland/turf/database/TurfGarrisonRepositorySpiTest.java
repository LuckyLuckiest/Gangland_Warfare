package org.luckyraven.gangland.turf.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.turf.powerups.Garrison;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** W7-F: the per-turf guard order survives a restart on the DatabaseBackend SPI path and old rows read as Defend. */
@DisplayName("TurfGarrisonRepository - hold order round trip")
class TurfGarrisonRepositorySpiTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari holds the .db handle past the test
	Path tempDir;

	private SqliteBackend backend;
	private JavaPlugin    plugin;

	@BeforeEach
	void setUp() throws SQLException {
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("garrison.db")));
		backend.applySchema(TableSchemas.fromTable(new TurfGarrisonTable()));
		plugin = PluginMocks.plugin(tempDir);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	private TurfGarrisonRepository repository() {
		return new TurfGarrisonRepository(plugin, mock(DatabaseHandler.class), backend);
	}

	private GarrisonManager restarted() {
		GarrisonManager manager = new GarrisonManager(repository());
		manager.initialize();
		return manager;
	}

	@Test
	@DisplayName("orderPersistsAcrossRestart: Hold and the stock are both read back by a fresh manager")
	void orderPersistsAcrossRestart() {
		GarrisonManager first = restarted();
		first.add(3, 2);
		first.setHolding(3, true);

		GarrisonManager second = restarted();

		assertTrue(second.holding(3));
		assertEquals(2, second.count(3));
	}

	@Test
	@DisplayName("existingTableGainsHold_oldRowsReadDefend: a pre-0.21.0 table gets the column and its rows read as Defend")
	void existingTableGainsHold_oldRowsReadDefend() throws SQLException {
		backend.execute("DROP TABLE turf_garrison");
		backend.execute("CREATE TABLE turf_garrison (turf_id INTEGER PRIMARY KEY, count INTEGER DEFAULT 0)");
		backend.execute("INSERT INTO turf_garrison (turf_id, count) VALUES (7, 4)");
		backend.applySchema(TableSchemas.fromTable(new TurfGarrisonTable()));

		Garrison row = repository().loadAll().stream().filter(g -> g.getTurfId() == 7).findFirst().orElseThrow();

		assertEquals(4, row.getCount());
		assertFalse(row.isHold());
	}

	@Test
	@DisplayName("defendRoundTrips: switching back to Defend is stored too")
	void defendRoundTrips() {
		GarrisonManager first = restarted();
		first.setHolding(3, true);
		first.setHolding(3, false);

		assertFalse(restarted().holding(3));
	}
}
