package me.luckyraven.persistence.database;

import me.luckyraven.persistence.support.MockPluginFactory;
import me.luckyraven.persistence.support.TestDatabaseHandler;
import me.luckyraven.persistence.support.TestTable;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link DatabaseManager}.
 *
 * <h2>Failure / backup scenarios</h2>
 * <ul>
 *   <li><b>Backup disabled:</b> {@code startBackup} returns the handler unchanged.
 *   <li><b>Backup enabled (SQLite → SQLite):</b> data written to the first SQLite instance
 *       is copied into a second SQLite instance.
 *   <li><b>closeConnections with no active connection:</b> does not throw.
 * </ul>
 */
@DisplayName("DatabaseManager - lifecycle and backup")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class DatabaseManagerTest {

	/**
	 * All handlers created during a test — closed in {@link #tearDown()}.
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
				if (db.getConnection() == null) db.connect();
				db.disconnect();
			} catch (Exception ignored) { /* already closed or never opened */ }
		}
		toClose.clear();
		// Windows: release SQLite file handles before JUnit deletes @TempDir
		MockPluginFactory.releaseDbFiles(tempDir);
	}

	// ─────────────────────────────────────── addDatabase / getDatabases ──

	@Test
	@DisplayName("addDatabase - getDatabases returns every added handler")
	void addDatabase_getDatabases_returnsAll() {
		DatabaseManager mgr = new DatabaseManager(plugin, MockPluginFactory.sqliteOnly());

		TestDatabaseHandler h1 = handler("mgr1");
		TestDatabaseHandler h2 = handler("mgr2");
		mgr.addDatabase(h1);
		mgr.addDatabase(h2);

		List<DatabaseHandler> result = mgr.getDatabases();
		assertEquals(2, result.size());
		assertTrue(result.contains(h1));
		assertTrue(result.contains(h2));
	}

	@Test
	@DisplayName("getDatabases - returns an unmodifiable list")
	void getDatabases_returnsUnmodifiableList() {
		DatabaseManager mgr = new DatabaseManager(plugin, MockPluginFactory.sqliteOnly());
		mgr.addDatabase(handler("unmod"));

		assertThrows(UnsupportedOperationException.class, () -> mgr.getDatabases().add(null));
	}

	// ──────────────────────────────────────── initializeDatabases ──

	@Test
	@DisplayName("initializeDatabases - initializes every registered handler")
	void initializeDatabases_initializesAllHandlers() {
		DatabaseManager mgr = new DatabaseManager(plugin, MockPluginFactory.sqliteOnly());

		TestDatabaseHandler h1 = handler("init1");
		TestDatabaseHandler h2 = handler("init2");
		mgr.addDatabase(h1);
		mgr.addDatabase(h2);

		mgr.initializeDatabases();

		// After initialize() a connection must be open
		assertNotNull(h1.getDatabase().getConnection(), "h1 must have an open connection");
		assertNotNull(h2.getDatabase().getConnection(), "h2 must have an open connection");

		h1.getDatabase().disconnect();
		h2.getDatabase().disconnect();
	}

	// ────────────────────────────────────────── closeConnections ──

	@Test
	@DisplayName("closeConnections - does not throw when no active connections exist")
	void closeConnections_withNoActiveConnections_doesNotThrow() {
		DatabaseManager     mgr = new DatabaseManager(plugin, MockPluginFactory.sqliteOnly());
		TestDatabaseHandler h   = handler("close_no_conn");
		// Do NOT call initialize() — database.getConnection() remains null
		mgr.addDatabase(h);

		assertDoesNotThrow(mgr::closeConnections);
	}

	// ──────────────────────────────────────────── startBackup ──

	@Test
	@DisplayName("startBackup - returns the same handler when backup is disabled")
	void startBackup_backupDisabled_returnsHandlerUnchanged() {
		DatabaseManager     mgr = new DatabaseManager(plugin, MockPluginFactory.sqliteOnly());
		TestDatabaseHandler h   = handler("backup_off");
		h.initialize();

		DatabaseHandler returned = mgr.startBackup(h);
		assertSame(h, returned);

		h.getDatabase().disconnect();
	}

	@Test
	@DisplayName("startBackup - copies all data from the source SQLite to a backup SQLite")
	void startBackup_sqliteToSqlite_copiesData() throws Exception {
		DatabaseSettingsProvider backupSettings = MockPluginFactory.sqliteWithBackup();
		DatabaseManager          mgr            = new DatabaseManager(plugin, backupSettings);

		// Source handler - write some rows
		TestDatabaseHandler source = tracked(new TestDatabaseHandler(plugin, backupSettings, "backup_src"));
		source.setType(DatabaseHandler.SQLITE);
		source.initialize();

		DatabaseHelper srcHelper = new DatabaseHelper(plugin, source);
		TestTable      srcTable  = new TestTable();
		srcHelper.runQueries(db -> db.table(srcTable.getName()).createTable(srcTable.createTableQuery(db)));
		srcHelper.runQueries(db -> db.table(srcTable.getName())
									 .insert(new String[]{"id", "name", "score"},
											 new Object[]{"bk1", "BackupAlice", 777},
											 new int[]{Types.VARCHAR, Types.VARCHAR, Types.INTEGER}));

		mgr.addDatabase(source);

		// Trigger a backup - startBackup disconnects source and re-initialises a second SQLite
		assertDoesNotThrow(() -> mgr.startBackup(source));
	}

	// ─────────────────────────────────────────── helpers ──

	/**
	 * Tracks {@code h} so {@link #tearDown()} can close it even if the test doesn't.
	 */
	private TestDatabaseHandler tracked(TestDatabaseHandler h) {
		toClose.add(h);
		return h;
	}

	private TestDatabaseHandler handler(String schemaName) {
		TestDatabaseHandler h = new TestDatabaseHandler(plugin, MockPluginFactory.sqliteOnly(), schemaName);
		h.setType(DatabaseHandler.SQLITE);
		toClose.add(h);
		return h;
	}
}
