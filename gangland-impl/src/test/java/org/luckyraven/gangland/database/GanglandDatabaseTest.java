package org.luckyraven.gangland.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.testkit.DatabaseSettingsMocks;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link GanglandDatabase#setType(int)} / {@code getSchema()} / {@code createSchema()} against the real (embedded,
 * fast-failing) legacy {@code Database} stack.
 *
 * <p>Pins Observation #1 (core-lifecycle.md, High risk / High confidence): with {@code Database.Type: mysql} and
 * {@code Database.SQLite.Failed_MySQL: false}, a failed MySQL connect leaves {@code getType() == MYSQL} and
 * {@code getDatabase() == null}, and the very next {@code database.createSchema()} call — which
 * {@code DatabaseConfig.ganglandDatabase(...)} makes unconditionally whenever the resolved type is MYSQL — throws a
 * raw {@link NullPointerException} instead of the {@code SQLException}/{@code IOException} that bean method's
 * {@code catch} clause expects, aborting {@code onEnable} with an undiagnosable stack trace.
 *
 * <p><b>Mechanism note</b> (verified against the actual Keystone 1.7.3 sources on disk, not just the audit's
 * description): {@code DatabaseHandler.enforceType(MYSQL)} today catches {@code SQLException | RuntimeException}
 * (not only {@code SQLException}) and correctly nulls {@code database} before rethrowing as a
 * {@code PluginException} — but {@code setType(MYSQL)}'s own catch of that {@code RuntimeException} calls
 * {@code useSQLite(...)}, which silently swallows it when {@code Failed_MySQL} is disabled. So
 * {@code setType(MYSQL)} itself never throws for this scenario; the crash is one call later, inside
 * {@code createSchema()}. The externally-visible defect (an undiagnosable NPE aborting bootstrap) matches the
 * audit exactly; only the internal "which catch clause" mechanism differs from some project notes.
 */
@DisplayName("GanglandDatabase")
class GanglandDatabaseTest {

	@TempDir(cleanup = CleanupMode.NEVER) // Windows: Hikari holds the .db handle past the test
	Path tempDir;

	private GanglandDatabase database;

	@AfterEach
	void tearDown() {
		if (database != null && database.getDatabase() != null) {
			database.getDatabase().disconnect();
		}
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("a freshly constructed handle reports MYSQL (DatabaseHandler's int type field defaults to 0) and getSchema returns the raw schema name")
	void getSchema_defaultType_returnsRawSchemaName() {
		database = new GanglandDatabase(PluginMocks.plugin(tempDir), "gangland", DatabaseSettingsMocks.sqliteOnly());

		assertEquals(DatabaseHandler.MYSQL, database.getType());
		assertEquals("gangland", database.getSchema());
		assertEquals("gangland", database.getSchemaName());
	}

	@Test
	@DisplayName("SQLITE type namespaces the schema under a database/ folder, and getSchemaName strips it back off")
	void getSchema_sqliteType_prependsDatabaseFolder() {
		database = new GanglandDatabase(PluginMocks.plugin(tempDir), "gangland", DatabaseSettingsMocks.sqliteOnly());

		database.setType(DatabaseHandler.SQLITE);

		assertEquals("database" + File.separator + "gangland", database.getSchema());
		assertEquals("gangland", database.getSchemaName());
		assertNotNull(database.getDatabase());
	}

	@Test
	@DisplayName("Observation #1 part 1 (core-lifecycle.md): MySQL fails with Failed_MySQL=false -> type stays MYSQL, database stays null")
	void setType_mysqlNoFallback_leavesTypeMysqlWithNullDatabase() {
		database = new GanglandDatabase(PluginMocks.plugin(tempDir), "gangland",
				DatabaseSettingsMocks.mysqlNoFallback());

		assertDoesNotThrow(() -> database.setType(DatabaseHandler.MYSQL),
				"setType(MYSQL) swallows the connection failure via useSQLite(...)'s early return when " +
						"Failed_MySQL is disabled, so it never throws here");

		assertEquals(DatabaseHandler.MYSQL, database.getType());
		assertNull(database.getDatabase());
	}

	@Test
	@DisplayName("Observation #1 part 2 (core-lifecycle.md): createSchema() on that state throws an undiagnosable NPE")
	void createSchema_afterFailedMysqlNoFallback_throwsNpe() {
		database = new GanglandDatabase(PluginMocks.plugin(tempDir), "gangland",
				DatabaseSettingsMocks.mysqlNoFallback());
		database.setType(DatabaseHandler.MYSQL);

		assertThrows(NullPointerException.class, database::createSchema,
				"pins the current undiagnosable-NPE behaviour; DatabaseConfig.ganglandDatabase(...) only catches " +
						"SQLException/IOException around this call, so this NPE escapes uncaught and aborts " +
						"onEnable");
	}

	@Test
	@DisplayName("Failed_MySQL=true falls back to a working embedded SQLite handle instead of leaving database null")
	void setType_mysqlWithSqliteFallback_fallsBackToWorkingSqlite() {
		database = new GanglandDatabase(PluginMocks.plugin(tempDir), "gangland",
				DatabaseSettingsMocks.mysqlWithSQLiteFallback());

		database.setType(DatabaseHandler.MYSQL);

		assertEquals(DatabaseHandler.SQLITE, database.getType());
		assertNotNull(database.getDatabase());
		assertEquals("database" + File.separator + "gangland", database.getSchema());
	}
}
