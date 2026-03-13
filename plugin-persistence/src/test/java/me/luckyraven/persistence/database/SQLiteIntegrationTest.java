package me.luckyraven.persistence.database;

import me.luckyraven.persistence.database.type.SQLite;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the {@link SQLite} {@link Database} implementation.
 *
 * <p>Each test method receives its own temporary directory (via JUnit 5's {@code @TempDir})
 * and a fresh SQLite connection, so tests are fully isolated from one another.
 *
 * <p>No Bukkit server is required. The {@link JavaPlugin} instance is mocked with Mockito;
 * only {@code getDataFolder()} needs to resolve (and only when {@code createSchema} / {@code dropSchema} are called,
 * which the core CRUD tests do not exercise).
 */
@DisplayName("SQLite - Database contract")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class SQLiteIntegrationTest {

	/**
	 * Fresh temp dir per test method — prevents cross-test state leakage. CleanupMode.NEVER: HikariCP/SQLite-JDBC holds
	 * native file handles briefly after pool shutdown on Windows; skipping JUnit's auto-delete avoids the "Failed to
	 * delete temp directory" error. OS temp-file cleanup handles the rest.
	 */
	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private SQLite db;

	@BeforeEach
	void setUp() throws SQLException {
		JavaPlugin mockPlugin = mock(JavaPlugin.class);
		when(mockPlugin.getDataFolder()).thenReturn(tempDir.toFile());

		db = new SQLite(mockPlugin);
		// Pass the absolute path directly; HikariCP creates the .db file automatically.
		db.initialize(Collections.emptyMap(), tempDir.resolve("test.db").toString());
		db.connect();
	}

	@AfterEach
	void tearDown() {
		// Disconnect only when the connection is still open; some tests close it early.
		try {
			if (db.getConnection() != null) db.disconnect();
		} catch (Exception ignored) { }
		// Windows: release SQLite file handles before JUnit deletes @TempDir
		me.luckyraven.persistence.support.MockPluginFactory.releaseDbFiles(tempDir);
	}

	// ───────────────────────────────────────────────────────── Connection ──

	@Test
	@DisplayName("connect - establishes an active JDBC connection")
	void connect_establishesActiveConnection() {
		assertNotNull(db.getConnection(), "Connection must be non-null after connect()");
	}

	@Test
	@DisplayName("connect - throws SQLException when a connection is already open")
	void connect_whenAlreadyConnected_throwsSQLException() {
		// setUp already called connect(); a second call must fail.
		assertThrows(SQLException.class, () -> db.connect());
	}

	@Test
	@DisplayName("handlesConnectionPool - returns true (HikariCP manages pooling)")
	void handlesConnectionPool_returnsTrue() {
		assertTrue(db.handlesConnectionPool());
	}

	// ─────────────────────────────────────────────────────── Table DDL ──

	@Test
	@DisplayName("createTable - registers the table in getTables()")
	void createTable_addsToTablesList() throws SQLException {
		db.table("players").createTable("uuid VARCHAR(36) PRIMARY KEY", "name VARCHAR(255)");
		assertTrue(db.getTables().contains("players"));
	}

	@Test
	@DisplayName("createTable - is idempotent (IF NOT EXISTS)")
	void createTable_isIdempotent() throws SQLException {
		db.table("players").createTable("uuid VARCHAR(36) PRIMARY KEY", "name VARCHAR(255)");
		assertDoesNotThrow(() -> db.table("players").createTable("uuid VARCHAR(36) PRIMARY KEY", "name VARCHAR(255)"));
	}

	@Test
	@DisplayName("deleteTable - removes the table from getTables()")
	void deleteTable_removesFromTablesList() throws SQLException {
		db.table("temp_tbl").createTable("id INTEGER PRIMARY KEY");
		db.table("temp_tbl").deleteTable();
		assertFalse(db.getTables().contains("temp_tbl"));
	}

	@Test
	@DisplayName("table - rejects identifiers that contain unsafe characters (SQL-injection guard)")
	void table_invalidIdentifier_throwsSQLException() {
		assertThrows(SQLException.class, () -> db.table("players; DROP TABLE players;--"),
					 "Unsafe table name must be rejected");
	}

	@Test
	@DisplayName("table - throws SQLException when called without a live connection")
	void table_withoutConnection_throwsSQLException() throws SQLException {
		db.disconnect();
		// Create a brand-new, uninitialised instance — no dataSource, no connection.
		SQLite fresh = new SQLite(mock(JavaPlugin.class));
		assertThrows(SQLException.class, () -> fresh.table("anything"));
	}

	// ─────────────────────────────────────────────────────── Insert / Select ──

	@Test
	@DisplayName("insert + select - round-trips a full row back correctly")
	void insert_andSelect_returnsCorrectValues() throws SQLException {
		db.table("users").createTable("uuid VARCHAR(36) PRIMARY KEY", "name VARCHAR(255)", "balance DOUBLE");
		db.table("users")
		  .insert(new String[]{"uuid", "name", "balance"}, new Object[]{"abc-123", "Alice", 100.5},
				  new int[]{Types.VARCHAR, Types.VARCHAR, Types.DOUBLE});

		Object[] result = db.table("users")
							.select("uuid = ?", new Object[]{"abc-123"}, new int[]{Types.VARCHAR},
									new String[]{"name", "balance"});

		assertEquals(2, result.length);
		assertEquals("Alice", result[0]);
		assertEquals(100.5, (double) result[1], 0.001);
	}

	@Test
	@DisplayName("select - returns empty array when no row matches the predicate")
	void select_noMatchingRow_returnsEmptyArray() throws SQLException {
		db.table("users").createTable("uuid VARCHAR(36) PRIMARY KEY", "name VARCHAR(255)");

		Object[] result = db.table("users")
							.select("uuid = ?", new Object[]{"does-not-exist"}, new int[]{Types.VARCHAR},
									new String[]{"name"});

		assertEquals(0, result.length);
	}

	@Test
	@DisplayName("selectAll - returns every inserted row")
	void selectAll_returnsAllInsertedRows() throws SQLException {
		db.table("items").createTable("id INTEGER PRIMARY KEY", "name VARCHAR(255)");
		db.table("items")
		  .insert(new String[]{"id", "name"}, new Object[]{1, "Sword"}, new int[]{Types.INTEGER, Types.VARCHAR});
		db.table("items")
		  .insert(new String[]{"id", "name"}, new Object[]{2, "Shield"}, new int[]{Types.INTEGER, Types.VARCHAR});
		db.table("items")
		  .insert(new String[]{"id", "name"}, new Object[]{3, "Axe"}, new int[]{Types.INTEGER, Types.VARCHAR});

		List<Object[]> rows = db.table("items").selectAll();
		assertEquals(3, rows.size());
	}

	@Test
	@DisplayName("insert - NullPointerException when table() has not been called first")
	void insert_withoutPriorTableCall_throwsException() {
		// The internal Preconditions.checkNotNull(table, ...) fires before the SQL is run.
		assertThrows(Exception.class,
					 () -> db.insert(new String[]{"col"}, new Object[]{"val"}, new int[]{Types.VARCHAR}));
	}

	// ──────────────────────────────────────────────────────────── Update ──

	@Test
	@DisplayName("update - modifies only the targeted row, leaves others untouched")
	void update_changesColumnValue() throws SQLException {
		db.table("members").createTable("id INTEGER PRIMARY KEY", "rank VARCHAR(255)");
		db.table("members")
		  .insert(new String[]{"id", "rank"}, new Object[]{1, "Recruit"}, new int[]{Types.INTEGER, Types.VARCHAR});
		db.table("members")
		  .insert(new String[]{"id", "rank"}, new Object[]{2, "Soldier"}, new int[]{Types.INTEGER, Types.VARCHAR});

		db.table("members")
		  .update("id = ?", new Object[]{1}, new int[]{Types.INTEGER}, new String[]{"rank"}, new Object[]{"Leader"},
				  new int[]{Types.VARCHAR});

		Object[] row1 = db.table("members")
						  .select("id = ?", new Object[]{1}, new int[]{Types.INTEGER}, new String[]{"rank"});
		Object[] row2 = db.table("members")
						  .select("id = ?", new Object[]{2}, new int[]{Types.INTEGER}, new String[]{"rank"});

		assertEquals("Leader", row1[0], "Row 1 rank should have been updated");
		assertEquals("Soldier", row2[0], "Row 2 rank should be unchanged");
	}

	// ──────────────────────────────────────────────────────────── Delete ──

	@Test
	@DisplayName("delete - by column removes only the matching row")
	void delete_byColumn_removesMatchingRow() throws SQLException {
		db.table("gangs").createTable("id INTEGER PRIMARY KEY", "name VARCHAR(255)");
		db.table("gangs")
		  .insert(new String[]{"id", "name"}, new Object[]{1, "Bloods"}, new int[]{Types.INTEGER, Types.VARCHAR});
		db.table("gangs")
		  .insert(new String[]{"id", "name"}, new Object[]{2, "Crips"}, new int[]{Types.INTEGER, Types.VARCHAR});

		db.table("gangs").delete("id", 1, Types.INTEGER);

		assertEquals(1, db.table("gangs").totalRows());
		Object[] remaining = db.table("gangs")
							   .select("id = ?", new Object[]{2}, new int[]{Types.INTEGER}, new String[]{"name"});
		assertEquals("Crips", remaining[0]);
	}

	@Test
	@DisplayName("delete - empty column clears all rows")
	void delete_allRows_clearsTable() throws SQLException {
		db.table("waypoints").createTable("id INTEGER PRIMARY KEY", "name VARCHAR(255)");
		db.table("waypoints")
		  .insert(new String[]{"id", "name"}, new Object[]{1, "Base"}, new int[]{Types.INTEGER, Types.VARCHAR});
		db.table("waypoints")
		  .insert(new String[]{"id", "name"}, new Object[]{2, "HQ"}, new int[]{Types.INTEGER, Types.VARCHAR});

		db.table("waypoints").delete("", null, Types.NULL);

		assertEquals(0, db.table("waypoints").totalRows());
	}

	// ──────────────────────────────────────────────────────── Row count ──

	@Test
	@DisplayName("totalRows - tracks insertions and deletions accurately")
	void totalRows_tracksInsertionsAndDeletions() throws SQLException {
		db.table("ranks").createTable("id INTEGER PRIMARY KEY", "name VARCHAR(255)");

		assertEquals(0, db.table("ranks").totalRows(), "Empty table must report 0 rows");

		db.table("ranks")
		  .insert(new String[]{"id", "name"}, new Object[]{1, "Soldier"}, new int[]{Types.INTEGER, Types.VARCHAR});
		assertEquals(1, db.table("ranks").totalRows());

		db.table("ranks")
		  .insert(new String[]{"id", "name"}, new Object[]{2, "Captain"}, new int[]{Types.INTEGER, Types.VARCHAR});
		assertEquals(2, db.table("ranks").totalRows());

		db.table("ranks").delete("id", 1, Types.INTEGER);
		assertEquals(1, db.table("ranks").totalRows());
	}

	// ─────────────────────────────────────────────────────── Schema ops ──

	@Test
	@DisplayName("addColumn - new column appears in getColumns()")
	void addColumn_appearsInGetColumns() throws SQLException {
		db.table("cops").createTable("id INTEGER PRIMARY KEY");
		db.table("cops").addColumn("name", "VARCHAR(255)");

		List<String> columns = db.table("cops").getColumns();
		assertTrue(columns.contains("name"), "Added column must be visible via getColumns()");
	}

	@Test
	@DisplayName("setTableName - renames the table and updates internal table list")
	void setTableName_renamesTable() throws SQLException {
		db.table("old_name").createTable("id INTEGER PRIMARY KEY");
		db.table("old_name").setTableName("new_name");

		assertFalse(db.getTables().contains("old_name"), "Old name must be gone");
		assertTrue(db.getTables().contains("new_name"), "New name must be present");
	}

	@Test
	@DisplayName("getColumns - returns the exact columns defined in createTable()")
	void getColumns_returnsAllDefinedColumns() throws SQLException {
		db.table("loot").createTable("id INTEGER PRIMARY KEY", "item VARCHAR(255)", "quantity INTEGER");

		List<String> columns = db.table("loot").getColumns();
		assertTrue(columns.contains("id"), "Column 'id' missing");
		assertTrue(columns.contains("item"), "Column 'item' missing");
		assertTrue(columns.contains("quantity"), "Column 'quantity' missing");
	}

	// ──────────────────────────────────────────────── Data-type helpers ──

	@Test
	@DisplayName("createList + getList - round-trip through CSV serialisation")
	void createList_getList_roundTrip() {
		List<String> original   = List.of("alpha", "beta", "gamma");
		String       serialized = db.createList(original);
		List<String> recovered  = db.getList(serialized);

		assertEquals(original, recovered);
	}

	@Test
	@DisplayName("isValidIdentifier - accepts safe names")
	void isValidIdentifier_acceptsSafeNames() {
		assertTrue(db.isValidIdentifier("valid_table"));
		assertTrue(db.isValidIdentifier("TableName123"));
		assertTrue(db.isValidIdentifier("_private"));
	}

	@Test
	@DisplayName("isValidIdentifier - rejects unsafe, blank, and null values")
	void isValidIdentifier_rejectsUnsafeValues() {
		assertFalse(db.isValidIdentifier("table; DROP TABLE users;--"), "SQL injection attempt must be rejected");
		assertFalse(db.isValidIdentifier(""), "Blank string must be rejected");
		assertFalse(db.isValidIdentifier(null), "null must be rejected");
		assertFalse(db.isValidIdentifier("123startsWithDigit"), "Leading digit must be rejected");
	}

	@Test
	@DisplayName("getStringDataType - maps JDBC type codes to correct SQLite type strings")
	void getStringDataType_mapsTypesCorrectly() {
		assertAll("SQLite type-string mappings", () -> assertEquals("INTEGER", db.getStringDataType(Types.INTEGER, 0)),
				  () -> assertEquals("INTEGER", db.getStringDataType(Types.BOOLEAN, 0)),
				  () -> assertEquals("INTEGER", db.getStringDataType(Types.TINYINT, 0)),
				  () -> assertEquals("INTEGER", db.getStringDataType(Types.SMALLINT, 0)),
				  () -> assertEquals("BIGINT", db.getStringDataType(Types.BIGINT, 0)),
				  () -> assertEquals("REAL", db.getStringDataType(Types.FLOAT, 0)),
				  () -> assertEquals("REAL", db.getStringDataType(Types.DOUBLE, 0)),
				// VARCHAR falls through to the default TEXT branch in SQLite
				  () -> assertEquals("TEXT", db.getStringDataType(Types.VARCHAR, 255)));
	}
}
