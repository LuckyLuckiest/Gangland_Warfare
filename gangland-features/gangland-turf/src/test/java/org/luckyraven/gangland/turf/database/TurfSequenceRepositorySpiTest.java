package org.luckyraven.gangland.turf.database;

import org.bukkit.plugin.java.JavaPlugin;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Proves the TF-05 high-water mark survives a restart on the DatabaseBackend SPI path: an empty {@code turf_sequence}
 * loads 0, a saved mark is read back by a brand-new repository over the same file, and a lower save never rewinds it.
 */
@DisplayName("TurfSequenceRepository — backend SPI round trip")
class TurfSequenceRepositorySpiTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari holds the .db handle past the test
	Path tempDir;

	private SqliteBackend backend;
	private JavaPlugin    plugin;

	@BeforeEach
	void setUp() throws SQLException {
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("sequence.db")));
		backend.applySchema(TableSchemas.fromTable(new TurfSequenceTable()));
		plugin = PluginMocks.plugin(tempDir);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	private TurfSequenceRepository repository() {
		return new TurfSequenceRepository(plugin, mock(DatabaseHandler.class), backend);
	}

	@Test
	@DisplayName("TF-05: an empty turf_sequence table loads a high-water mark of 0")
	void emptyTable_loadsZero() {
		assertEquals(0, repository().loadHighWaterMark());
	}

	@Test
	@DisplayName("TF-05: a mark saved by one repository is read back by a fresh repository on the same file")
	void saveThenNewRepository_loadsHighest() {
		repository().saveHighWaterMark(7);

		assertEquals(7, repository().loadHighWaterMark());
	}

	@Test
	@DisplayName("TF-05: a lower save never rewinds the stored mark")
	void save_keepsTheMaximum() {
		TurfSequenceRepository repository = repository();
		repository.saveHighWaterMark(9);
		repository.saveHighWaterMark(4);

		assertEquals(9, repository().loadHighWaterMark());
	}
}
