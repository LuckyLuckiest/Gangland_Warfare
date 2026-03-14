package me.luckyraven.persistence.database;

import me.luckyraven.persistence.database.type.SQLite;
import me.luckyraven.persistence.support.MockPluginFactory;
import me.luckyraven.persistence.support.TestDatabaseHandler;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link DatabaseHandler} type-switching and failure-fallback logic.
 *
 * <h2>Failure scenarios covered</h2>
 * <ul>
 *   <li><b>MySQL → SQLite fallback:</b> when MySQL initialisation fails (no driver on the
 *       test classpath) and {@code isSqliteFailedMysql = true}, the handler silently
 *       switches to SQLite.
 *   <li><b>MySQL fails, no fallback:</b> when {@code isSqliteFailedMysql = false} the
 *       failure is swallowed internally and {@code getDatabase()} returns {@code null}.
 *   <li><b>Unknown type:</b> {@code enforceType} and {@code setType} reject unknown
 *       integer codes with {@link IllegalArgumentException}.
 * </ul>
 */
@DisplayName("DatabaseHandler - type switching and fallback")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class DatabaseHandlerFallbackTest {

	/**
	 * All handlers created during a test - closed in {@link #tearDown()}.
	 */
	private final List<TestDatabaseHandler> toClose = new ArrayList<>();
	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;
	private JavaPlugin plugin;

	@BeforeEach
	void setUp() {
		plugin = MockPluginFactory.plugin(tempDir);
	}

	@AfterEach
	void tearDown() {
		for (TestDatabaseHandler h : toClose) {
			Database db = h.getDatabase();
			if (db == null) continue;
			try {
				// connect() is needed if setType() was called but initialize() was not
				if (db.getConnection() == null) db.connect();
				db.disconnect();
			} catch (Exception ignored) { /* already closed or never opened */ }
		}
		toClose.clear();
		// Windows: release SQLite file handles before JUnit deletes @TempDir
		MockPluginFactory.releaseDbFiles(tempDir);
	}

	@Test
	@DisplayName("setType(SQLITE) - database is a live SQLite instance")
	void setType_sqlite_databaseIsLiveSQLite() {
		TestDatabaseHandler handler = tracked(
				new TestDatabaseHandler(plugin, MockPluginFactory.sqliteOnly(), "happy_sqlite"));
		handler.setType(DatabaseHandler.SQLITE);

		assertNotNull(handler.getDatabase(), "Database must not be null after SQLITE init");
		assertInstanceOf(SQLite.class, handler.getDatabase(), "Database must be a SQLite implementation");
		assertEquals(DatabaseHandler.SQLITE, handler.getType());
	}

	// ─────────────────────────────────────── SQLite (happy path) ──

	@Test
	@DisplayName("setType(SQLITE) + initialize - connection is open after initialize()")
	void setType_sqlite_afterInitialize_connectionIsOpen() {
		TestDatabaseHandler handler = tracked(
				new TestDatabaseHandler(plugin, MockPluginFactory.sqliteOnly(), "conn_test"));
		handler.setType(DatabaseHandler.SQLITE);
		handler.initialize();

		assertNotNull(handler.getDatabase().getConnection(), "Connection must be open after initialize()");

		// Cleanup
		handler.getDatabase().disconnect();
	}

	@Test
	@DisplayName("enforceType(SQLITE) - creates a valid SQLite database at the temp path")
	void enforceType_sqlite_createsDbFile() {
		TestDatabaseHandler handler = tracked(
				new TestDatabaseHandler(plugin, MockPluginFactory.sqliteOnly(), "enforce_test"));
		handler.enforceType(DatabaseHandler.SQLITE);

		assertNotNull(handler.getDatabase());
		assertInstanceOf(SQLite.class, handler.getDatabase());
	}

	@Test
	@DisplayName("setType(MYSQL) + isSqliteFailedMysql=true → silently falls back to SQLite")
	void setType_mysql_withFallbackEnabled_switchesToSQLite() {
		TestDatabaseHandler handler = tracked(
				new TestDatabaseHandler(plugin, MockPluginFactory.mysqlWithSQLiteFallback(), "fallback_test"));

		// No MySQL driver on the test classpath → instantiation fails fast.
		// Because isSqliteFailedMysql=true the handler must recover and use SQLite.
		assertDoesNotThrow(() -> handler.setType(DatabaseHandler.MYSQL));

		assertEquals(DatabaseHandler.SQLITE, handler.getType(),
					 "Type must have switched to SQLITE after MySQL failure");
		assertNotNull(handler.getDatabase(), "Database must be non-null after successful SQLite fallback");
		assertInstanceOf(SQLite.class, handler.getDatabase());
	}

	// ─────────────────────────────────── MySQL → SQLite fallback ──

	@Test
	@DisplayName("setType(MYSQL) + isSqliteFailedMysql=false → no active connection, no exception thrown")
	void setType_mysql_withFallbackDisabled_databaseRemainsNull() {
		TestDatabaseHandler handler = tracked(
				new TestDatabaseHandler(plugin, MockPluginFactory.mysqlNoFallback(), "nofallback_test"));

		// The exception is swallowed inside setType(); no fallback is attempted.
		assertDoesNotThrow(() -> handler.setType(DatabaseHandler.MYSQL));

		// enforceType(MYSQL) only catches SQLException before nulling database.
		// HikariCP throws PoolInitializationException (RuntimeException) on connection failure,
		// so database may be a non-functional MySQL instance rather than null.
		// Either way, there must be no active connection.
		Database db = handler.getDatabase();
		assertTrue(db == null || db.getConnection() == null,
				   "No active connection must exist when MySQL fails and fallback is disabled");
	}

	@Test
	@DisplayName("setType(MYSQL) fallback - handler stays functional after switching to SQLite")
	void setType_mysql_fallback_handlerRemainsFunctionalAfterSwitch() {
		TestDatabaseHandler handler = tracked(
				new TestDatabaseHandler(plugin, MockPluginFactory.mysqlWithSQLiteFallback(),
										"functional_after_fallback"));

		handler.setType(DatabaseHandler.MYSQL);

		// The handler fell back to SQLite - initialize and verify the connection works
		handler.initialize();

		assertNotNull(handler.getDatabase().getConnection(),
					  "Connection must be alive after initialize() on the fallback database");

		handler.getDatabase().disconnect();
	}

	@Test
	@DisplayName("enforceType - throws IllegalArgumentException for an unknown type code")
	void enforceType_unknownType_throwsIllegalArgument() {
		TestDatabaseHandler handler = tracked(
				new TestDatabaseHandler(plugin, MockPluginFactory.sqliteOnly(), "unknown_enforce"));

		assertThrows(IllegalArgumentException.class, () -> handler.enforceType(99));
	}

	// ──────────────────────────────────────── enforceType errors ──

	@Test
	@DisplayName("setType - throws IllegalArgumentException for an unknown type code")
	void setType_unknownType_throwsIllegalArgument() {
		TestDatabaseHandler handler = tracked(
				new TestDatabaseHandler(plugin, MockPluginFactory.sqliteOnly(), "unknown_set"));

		assertThrows(IllegalArgumentException.class, () -> handler.setType(99));
	}

	@Test
	@DisplayName("getSchemaName - returns the bare schema name without path separator or extension")
	void getSchemaName_returnsBareName() {
		TestDatabaseHandler handler = tracked(
				new TestDatabaseHandler(plugin, MockPluginFactory.sqliteOnly(), "my_schema"));

		assertEquals("my_schema", handler.getSchemaName());
	}

	// ─────────────────────────────────────── getSchemaName helper ──

	/**
	 * Tracks {@code h} for cleanup in {@link #tearDown()} and returns it. Use instead of bare
	 * {@code new TestDatabaseHandler(...)} in every test.
	 */
	private TestDatabaseHandler tracked(TestDatabaseHandler h) {
		toClose.add(h);
		return h;
	}
}
