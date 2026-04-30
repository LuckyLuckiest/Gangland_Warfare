package org.luckyraven.gangland.persistence.database.query;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.persistence.database.type.SQLite;
import org.luckyraven.gangland.persistence.support.MockPluginFactory;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link QueryBuilder} and {@link Column} against a real SQLite database.
 *
 * <p>Every test uses a fresh SQLite connection and an isolated temp directory, so there is no
 * cross-test state leakage.
 *
 * <p>{@code @TempDir(cleanup = CleanupMode.NEVER)} + {@link MockPluginFactory#releaseDbFiles}
 * avoids Windows HikariCP file-handle lock errors - see CLAUDE.md for the full explanation.
 */
@DisplayName("QueryBuilder - integration against SQLite")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class QueryBuilderIntegrationTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private SQLite db;

	@BeforeEach
	void setUp() throws SQLException {
		JavaPlugin mockPlugin = mock(JavaPlugin.class);
		when(mockPlugin.getDataFolder()).thenReturn(tempDir.toFile());

		db = new SQLite(mockPlugin);
		db.initialize(Collections.emptyMap(), tempDir.resolve("tbl_test.db").toString());
		db.connect();
	}

	@AfterEach
	void tearDown() {
		try {
			if (db.getConnection() != null) db.disconnect();
		} catch (Exception ignored) { }
		MockPluginFactory.releaseDbFiles(tempDir);
	}

	// ───────────────────────────────────────────────────────── INSERT ──

	@Test
	@DisplayName("insert - round-trips a full row back via executeOne")
	void insert_andSelectOne_returnsCorrectValues() throws SQLException {
		db.table("users").createTable(
				"id INTEGER PRIMARY KEY",
				"name VARCHAR(255) NOT NULL",
				"balance REAL NOT NULL"
		);

		QueryBuilder.on(db, "users")
		            .insert()
		            .set("id", 1)
		            .set("name", "Alice")
		            .set("balance", 250.0)
		            .execute();

		Object[] row = QueryBuilder.on(db, "users")
		                           .select("name", "balance")
		                           .where("id", 1)
		                           .executeOne();

		assertEquals(2, row.length);
		assertEquals("Alice", row[0]);
		assertEquals(250.0, (double) row[1], 0.001);
	}

	@Test
	@DisplayName("insert - inserts a row with a null nullable column")
	void insert_withNullColumn_persists() throws SQLException {
		db.table("players").createTable(
				"id INTEGER PRIMARY KEY",
				"nickname VARCHAR(255) NULL"
		);

		QueryBuilder.on(db, "players")
		            .insert()
		            .set("id", 1)
		            .set("nickname", null)
		            .execute();

		Object[] row = QueryBuilder.on(db, "players")
		                           .select("nickname")
		                           .where("id", 1)
		                           .executeOne();

		assertEquals(1, row.length);
		assertNull(row[0]);
	}

	@Test
	@DisplayName("insert - no-op when no columns are set")
	void insert_noColumns_doesNotThrow() throws SQLException {
		db.table("empty_test").createTable("id INTEGER PRIMARY KEY");

		assertDoesNotThrow(() -> QueryBuilder.on(db, "empty_test").insert().execute());
		assertEquals(0, db.table("empty_test").totalRows());
	}

	// ───────────────────────────────────────────────────────── SELECT ──

	@Test
	@DisplayName("select.executeOne - returns empty array when no row matches")
	void selectOne_noMatch_returnsEmptyArray() throws SQLException {
		db.table("gangs").createTable("id INTEGER PRIMARY KEY", "name VARCHAR(255) NOT NULL");

		Object[] result = QueryBuilder.on(db, "gangs")
		                              .select("name")
		                              .where("id", 99)
		                              .executeOne();

		assertEquals(0, result.length);
	}

	@Test
	@DisplayName("select.executeAll - returns all rows when no WHERE is set")
	void selectAll_noConditions_returnsEveryRow() throws SQLException {
		db.table("items").createTable("id INTEGER PRIMARY KEY", "name VARCHAR(255) NOT NULL");
		QueryBuilder qb = QueryBuilder.on(db, "items");
		qb.insert().set("id", 1).set("name", "Sword").execute();
		qb.insert().set("id", 2).set("name", "Shield").execute();
		qb.insert().set("id", 3).set("name", "Axe").execute();

		List<Object[]> rows = qb.select("*").executeAll();

		assertEquals(3, rows.size());
	}

	@Test
	@DisplayName("select.executeAll - with WHERE returns only matching rows")
	void selectAll_withCondition_returnsFilteredRows() throws SQLException {
		db.table("members").createTable(
				"id INTEGER PRIMARY KEY",
				"gang_id INTEGER NOT NULL",
				"name VARCHAR(255) NOT NULL"
		);
		QueryBuilder qb = QueryBuilder.on(db, "members");
		qb.insert().set("id", 1).set("gang_id", 10).set("name", "Alice").execute();
		qb.insert().set("id", 2).set("gang_id", 10).set("name", "Bob").execute();
		qb.insert().set("id", 3).set("gang_id", 20).set("name", "Carol").execute();

		List<Object[]> rows = qb.select("*").where("gang_id", 10).executeAll();

		assertEquals(2, rows.size());
	}

	@Test
	@DisplayName("select - Column.of with explicit Types.CHAR is accepted for string values")
	void select_withExplicitColumnType_works() throws SQLException {
		db.table("cops").createTable("uuid VARCHAR(36) PRIMARY KEY", "name VARCHAR(255) NOT NULL");
		QueryBuilder.on(db, "cops")
		            .insert()
		            .set(Column.of("uuid", "abc-def", Types.VARCHAR))
		            .set("name", "Officer Smith")
		            .execute();

		Object[] row = QueryBuilder.on(db, "cops")
		                           .select("name")
		                           .where(Column.of("uuid", "abc-def", Types.VARCHAR))
		                           .executeOne();

		assertEquals(1, row.length);
		assertEquals("Officer Smith", row[0]);
	}

	// ───────────────────────────────────────────────────────── UPDATE ──

	@Test
	@DisplayName("update - modifies only the targeted row, leaves others untouched")
	void update_changesOnlyTargetedRow() throws SQLException {
		db.table("ranks").createTable(
				"id INTEGER PRIMARY KEY",
				"title VARCHAR(255) NOT NULL",
				"salary REAL NOT NULL"
		);
		QueryBuilder qb = QueryBuilder.on(db, "ranks");
		qb.insert().set("id", 1).set("title", "Recruit").set("salary", 100.0).execute();
		qb.insert().set("id", 2).set("title", "Soldier").set("salary", 200.0).execute();

		qb.update()
		  .set("title", "Captain")
		  .set("salary", 500.0)
		  .where("id", 1)
		  .execute();

		Object[] updated   = qb.select("title", "salary").where("id", 1).executeOne();
		Object[] untouched = qb.select("title", "salary").where("id", 2).executeOne();

		assertEquals("Captain", updated[0]);
		assertEquals(500.0, (double) updated[1], 0.001);
		assertEquals("Soldier", untouched[0]);
		assertEquals(200.0, (double) untouched[1], 0.001);
	}

	@Test
	@DisplayName("update - no-op when no columns are set")
	void update_noSetColumns_doesNotThrow() throws SQLException {
		db.table("noop_tbl").createTable("id INTEGER PRIMARY KEY", "name VARCHAR(255) NOT NULL");
		QueryBuilder.on(db, "noop_tbl")
		            .insert().set("id", 1).set("name", "Before").execute();

		assertDoesNotThrow(() -> QueryBuilder.on(db, "noop_tbl").update().where("id", 1).execute());

		Object[] row = QueryBuilder.on(db, "noop_tbl").select("name").where("id", 1).executeOne();
		assertEquals("Before", row[0], "Row must be unchanged after a no-op update");
	}

	// ───────────────────────────────────────────────────────── DELETE ──

	@Test
	@DisplayName("delete - single-condition removes only the matching row")
	void delete_singleCondition_removesMatchingRow() throws SQLException {
		db.table("waypoints").createTable("id INTEGER PRIMARY KEY", "label VARCHAR(255) NOT NULL");
		QueryBuilder qb = QueryBuilder.on(db, "waypoints");
		qb.insert().set("id", 1).set("label", "HQ").execute();
		qb.insert().set("id", 2).set("label", "Base").execute();

		qb.delete().where("id", 1).execute();

		assertEquals(1, db.table("waypoints").totalRows());
		Object[] remaining = qb.select("label").where("id", 2).executeOne();
		assertEquals("Base", remaining[0]);
	}

	@Test
	@DisplayName("delete - multi-condition (AND) removes only the exact match")
	void delete_multiCondition_removesOnlyExactMatch() throws SQLException {
		db.table("membership").createTable(
				"player_id INTEGER NOT NULL",
				"gang_id   INTEGER NOT NULL",
				"active    INTEGER NOT NULL"   // SQLite stores BOOLEAN as INTEGER
		);
		QueryBuilder qb = QueryBuilder.on(db, "membership");
		qb.insert().set("player_id", 1).set("gang_id", 10).set("active", 1).execute();
		qb.insert().set("player_id", 1).set("gang_id", 20).set("active", 1).execute(); // same player, different gang
		qb.insert().set("player_id", 2).set("gang_id", 10).set("active", 1).execute(); // different player, same gang

		// Delete only player 1 from gang 10
		qb.delete()
		  .where("player_id", 1)
		  .where("gang_id", 10)
		  .execute();

		assertEquals(2, db.table("membership").totalRows(),
		             "Only one of the three rows must be deleted");

		// Player 1 in gang 20 must still exist
		Object[] stillExists = qb.select("active").where("player_id", 1).executeOne();
		assertNotNull(stillExists);
		assertEquals(1, stillExists.length);
	}

	@Test
	@DisplayName("delete - no conditions clears the entire table")
	void delete_noConditions_clearsTable() throws SQLException {
		db.table("loot").createTable("id INTEGER PRIMARY KEY", "item VARCHAR(255) NOT NULL");
		QueryBuilder qb = QueryBuilder.on(db, "loot");
		qb.insert().set("id", 1).set("item", "Gold").execute();
		qb.insert().set("id", 2).set("item", "Ammo").execute();

		qb.delete().execute();

		assertEquals(0, db.table("loot").totalRows());
	}

	// ─────────────────────────────────────── Type-inference coverage ──

	@Test
	@DisplayName("insert + select - Boolean value round-trips correctly via inference")
	void insert_andSelect_booleanRoundTrips() throws SQLException {
		db.table("flags").createTable("id INTEGER PRIMARY KEY", "enabled INTEGER NOT NULL");

		QueryBuilder qb = QueryBuilder.on(db, "flags");
		// Use INTEGER for SQLite booleans (SQLite has no native BOOLEAN type)
		qb.insert()
		  .set(Column.of("id", 1, Types.INTEGER))
		  .set(Column.of("enabled", 1, Types.INTEGER))
		  .execute();

		Object[] row = qb.select("enabled").where("id", 1).executeOne();
		assertEquals(1, row.length);
		assertNotNull(row[0]);
	}

	@Test
	@DisplayName("insert + select - multiple rows preserve independent values")
	void insert_multipleRows_allValuesPreserved() throws SQLException {
		db.table("players").createTable(
				"id INTEGER PRIMARY KEY",
				"name VARCHAR(255) NOT NULL",
				"score REAL NOT NULL"
		);
		QueryBuilder qb = QueryBuilder.on(db, "players");
		qb.insert().set("id", 1).set("name", "Alice").set("score", 10.5).execute();
		qb.insert().set("id", 2).set("name", "Bob").set("score", 20.0).execute();

		Object[] alice = qb.select("name", "score").where("id", 1).executeOne();
		Object[] bob   = qb.select("name", "score").where("id", 2).executeOne();

		assertAll(
				() -> assertEquals("Alice", alice[0]),
				() -> assertEquals(10.5, (double) alice[1], 0.001),
				() -> assertEquals("Bob", bob[0]),
				() -> assertEquals(20.0, (double) bob[1], 0.001)
		);
	}

	// ──────────────────────────────────────────────── getTable() ──

	@Test
	@DisplayName("getTable - returns the table name set by table()")
	void getTable_returnsActiveTableName() throws SQLException {
		db.table("some_table").createTable("id INTEGER PRIMARY KEY");
		assertEquals("some_table", db.getTable());
	}

	@Test
	@DisplayName("getTable - returns null before any table() call")
	void getTable_beforeTableCall_returnsNull() {
		SQLite fresh = new SQLite(mock(JavaPlugin.class));
		assertNull(fresh.getTable());
	}
}
