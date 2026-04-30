package org.luckyraven.gangland.persistence.component;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.persistence.database.Database;
import org.luckyraven.gangland.persistence.database.DatabaseHandler;
import org.luckyraven.gangland.persistence.database.DatabaseHelper;
import org.luckyraven.gangland.persistence.database.component.Attribute;
import org.luckyraven.gangland.persistence.database.component.Table;
import org.luckyraven.gangland.persistence.support.MockPluginFactory;
import org.luckyraven.gangland.persistence.support.TestDatabaseHandler;
import org.luckyraven.gangland.persistence.support.TestEntity;
import org.luckyraven.gangland.persistence.support.TestTable;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link Table} - {@code createTableQuery}, {@code insertTableQuery}, {@code updateTableQuery},
 * {@code validateSchema} - all backed by real SQLite.
 */
@DisplayName("Table - DDL generation and DML operations")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class TableIntegrationTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private DatabaseHandler handler;
	private DatabaseHelper  helper;
	private TestTable       table;

	@BeforeEach
	void setUp() {
		JavaPlugin plugin = MockPluginFactory.plugin(tempDir);
		handler = new TestDatabaseHandler(plugin, MockPluginFactory.sqliteOnly(), "tbl_test");
		handler.setType(DatabaseHandler.SQLITE);
		// opens the connection (stays open with HikariCP)
		handler.initialize();
		helper = new DatabaseHelper(plugin, handler);
		table  = new TestTable();

		// Physically create the table so DML tests have something to work with.
		helper.runQueries(db -> db.table(table.getName()).createTable(table.createTableQuery(db)));
	}

	@AfterEach
	void tearDown() {
		Database db = handler.getDatabase();
		if (db != null && db.getConnection() != null) db.disconnect();
		// Windows: release SQLite file handles before JUnit deletes @TempDir
		MockPluginFactory.releaseDbFiles(tempDir);
	}

	// ──────────────────────────────────────────── createTableQuery ──

	@Test
	@DisplayName("createTableQuery - produces a DDL fragment per attribute")
	void createTableQuery_generatesOneFragmentPerAttribute() {
		helper.runQueries(db -> {
			String[] ddl = table.createTableQuery(db);
			// id, name, score → three fragments
			assertEquals(3, ddl.length, "Expected one DDL fragment per attribute, got: " + Arrays.toString(ddl));
		});
	}

	@Test
	@DisplayName("createTableQuery - primary key fragment contains PRIMARY KEY keyword")
	void createTableQuery_primaryKeyAttributeHasPrimaryKeyClause() {
		helper.runQueries(db -> {
			String[] ddl = table.createTableQuery(db);
			// fragment 0 corresponds to 'id' (primary key)
			assertTrue(ddl[0].toUpperCase().contains("PRIMARY KEY"), "Expected 'PRIMARY KEY' in fragment: " + ddl[0]);
		});
	}

	@Test
	@DisplayName("createTableQuery - includes FOREIGN KEY clause when FK is configured")
	void createTableQuery_withForeignKey_addsForeignKeyFragment() {
		// Build a child table whose entity_id references test_entity.id
		Table<TestEntity> childTable = new Table<>("child_table") {
			@Override
			public Object[] getData(TestEntity e) {
				return new Object[]{e.getId()};
			}

			@Override
			public Map<String, Object> searchCriteria(TestEntity e) {
				return createSearchCriteria("entity_id = ?", new Object[]{e.getId()}, new int[]{Types.VARCHAR},
				                            new int[]{0});
			}
		};

		Attribute<String> fkAttr   = new Attribute<>("entity_id", false, String.class);
		Attribute<String> parentPk = new Attribute<>("id", true, String.class);
		fkAttr.setForeignKey(parentPk, table);

		// Use reflection-accessible addAttribute via package-private bridge - instead,
		// rely on the fact that Table exposes addAttribute as protected. We subclass inline.
		Table<TestEntity> childWithFk = new Table<>("child_fk") {
			{
				addAttribute(fkAttr);
			}

			@Override
			public Object[] getData(TestEntity e) {
				return new Object[]{e.getId()};
			}

			@Override
			public Map<String, Object> searchCriteria(TestEntity e) {
				return createSearchCriteria("entity_id = ?", new Object[]{e.getId()}, new int[]{Types.VARCHAR},
				                            new int[]{0});
			}
		};

		helper.runQueries(db -> {
			String[] ddl         = childWithFk.createTableQuery(db);
			boolean  hasFkClause = Arrays.stream(ddl).anyMatch(s -> s.toUpperCase().startsWith("FOREIGN KEY"));
			assertTrue(hasFkClause, "Expected a FOREIGN KEY fragment in DDL array");
		});
	}

	// ─────────────────────────────────────── insertTableQuery / DML ──

	@Test
	@DisplayName("insertTableQuery - persists a new row that is readable via selectAll")
	void insertTableQuery_persistsRow() {
		TestEntity entity = new TestEntity("e1", "Alice", 100);

		helper.runQueries(db -> table.insertTableQuery(db, entity));

		helper.runQueries(db -> {
			List<Object[]> rows = db.table(TestTable.TABLE_NAME).selectAll();
			assertEquals(1, rows.size());
			assertEquals("e1", rows.getFirst()[0]);
			assertEquals("Alice", rows.getFirst()[1]);
		});
	}

	@Test
	@DisplayName("insertTableQuery - getData() returning empty array is a no-op")
	void insertTableQuery_emptyData_isNoOp() {
		Table<String> emptyTable = new Table<>("empty_tbl") {
			@Override
			public Object[] getData(String s) { return new Object[0]; }

			@Override
			public Map<String, Object> searchCriteria(String s) {
				return createSearchCriteria("", new Object[]{ }, new int[]{ }, new int[]{ });
			}
		};
		// No insert should be attempted; runQueries must not throw.
		assertDoesNotThrow(() -> helper.runQueries(db -> emptyTable.insertTableQuery(db, "anything")));
	}

	@Test
	@DisplayName("updateTableQuery - modifies only the non-PK columns of the matching row")
	void updateTableQuery_modifiesNonPkColumns() {
		TestEntity original = new TestEntity("u1", "Bob", 50);
		TestEntity updated  = new TestEntity("u1", "Bobby", 99);

		helper.runQueries(db -> table.insertTableQuery(db, original));
		helper.runQueries(db -> table.updateTableQuery(db, updated));

		helper.runQueries(db -> {
			Object[] row = db.table(TestTable.TABLE_NAME)
			                 .select("id = ?", new Object[]{"u1"}, new int[]{Types.VARCHAR},
			                         new String[]{"name", "score"});
			assertEquals("Bobby", row[0], "name should be updated");
			assertEquals(99, ((Number) row[1]).intValue(), "score should be updated");
		});
	}

	@Test
	@DisplayName("updateTableQuery - does NOT change the primary key column")
	void updateTableQuery_primaryKeyRemainsUnchanged() {
		TestEntity original = new TestEntity("pk1", "Charlie", 10);
		TestEntity updated  = new TestEntity("pk1", "Charlie Updated", 20);

		helper.runQueries(db -> table.insertTableQuery(db, original));
		helper.runQueries(db -> table.updateTableQuery(db, updated));

		helper.runQueries(db -> {
			Object[] row = db.table(TestTable.TABLE_NAME)
			                 .select("id = ?", new Object[]{"pk1"}, new int[]{Types.VARCHAR}, new String[]{"id"});
			assertEquals("pk1", row[0], "Primary key must not change");
		});
	}

	// ────────────────────────────────────────────── validateSchema ──

	@Test
	@DisplayName("validateSchema - adds a column that is defined in the Table but absent from the DB")
	void validateSchema_addsNewColumn() {
		// 1. Create an extended table definition that has an extra 'bonus' column
		Table<TestEntity> extendedTable = new Table<>(TestTable.TABLE_NAME) {
			{
				addAttribute(new Attribute<>("id", true, String.class));
				addAttribute(new Attribute<>("name", false, String.class));
				addAttribute(new Attribute<>("score", false, Integer.class));
				Attribute<Integer> bonus = new Attribute<>("bonus", false, Integer.class);
				bonus.setCanBeNull(true);
				addAttribute(bonus);
			}

			@Override
			public Object[] getData(TestEntity e) { return null; }

			@Override
			public Map<String, Object> searchCriteria(TestEntity e) { return null; }
		};

		// 2. Run validateSchema - should ADD the 'bonus' column
		helper.runQueries(extendedTable::validateSchema);

		// 3. Verify 'bonus' now exists
		helper.runQueries(db -> {
			List<String> cols = db.table(TestTable.TABLE_NAME).getColumns();
			assertTrue(cols.contains("bonus"), "validateSchema must add the missing 'bonus' column; columns: " + cols);
		});
	}

	@Test
	@DisplayName("validateSchema - drops a column that is in the DB but absent from the Table definition")
	void validateSchema_dropsObsoleteColumn() {
		// 1. Add an extra column to the live DB that is NOT in the table definition
		helper.runQueries(db -> db.table(TestTable.TABLE_NAME).addColumn("obsolete_col", "TEXT"));

		// 2. Run validateSchema with the original table (no 'obsolete_col' attribute)
		helper.runQueries(db -> table.validateSchema(db));

		// 3. Verify 'obsolete_col' was dropped
		helper.runQueries(db -> {
			List<String> cols = db.table(TestTable.TABLE_NAME).getColumns();
			assertFalse(cols.contains("obsolete_col"),
			            "validateSchema must drop the obsolete column; columns: " + cols);
		});
	}

	@Test
	@DisplayName("validateSchema - is a no-op when the DB schema already matches the definition")
	void validateSchema_freshTable_noChanges() {
		helper.runQueries(db -> {
			List<String> before = db.table(TestTable.TABLE_NAME).getColumns();
			table.validateSchema(db);
			List<String> after = db.table(TestTable.TABLE_NAME).getColumns();
			assertEquals(before, after, "No schema changes should occur on a fresh, matching table");
		});
	}

	@Test
	@DisplayName("validateSchema - adds a NOT NULL column with an auto-inferred default")
	void validateSchema_notNullColumnWithoutDefault_usesInferredDefault() {
		Table<TestEntity> withNotNull = new Table<>(TestTable.TABLE_NAME) {
			{
				addAttribute(new Attribute<>("id", true, String.class));
				addAttribute(new Attribute<>("name", false, String.class));
				addAttribute(new Attribute<>("score", false, Integer.class));
				Attribute<Integer> level = new Attribute<>("level", false, Integer.class);
				level.setCanBeNull(false);  // NOT NULL with no default → must infer '0'
				addAttribute(level);
			}

			@Override
			public Object[] getData(TestEntity e) {
				return null;
			}

			@Override
			public Map<String, Object> searchCriteria(TestEntity e) {
				return null;
			}
		};

		// Insert a row first so that adding a NOT NULL column challenges existing rows
		helper.runQueries(db -> table.insertTableQuery(db, new TestEntity("v1", "Dave", 5)));

		// validateSchema should add 'level NOT NULL DEFAULT 0'
		assertDoesNotThrow(() -> helper.runQueries(withNotNull::validateSchema));

		helper.runQueries(db -> {
			List<String> cols = db.table(TestTable.TABLE_NAME).getColumns();
			assertTrue(cols.contains("level"), "The 'level' column must have been added");
		});
	}

	// ──────────────────────────── validateSchema – nullability migration ──

	@Test
	@DisplayName("validateSchema - changes NOT NULL column to NULL; PRAGMA confirms notnull=0 after migration")
	void validateSchema_notNullToNullable_columnAcceptsNullAfterMigration() {
		helper.runQueries(db -> table.insertTableQuery(db, new TestEntity("m1", "Alice", 42)));

		helper.runQueries(makeTestEntityTable(true)::validateSchema);

		assertEquals(0, queryScoreNotNullFlag(), "score notnull flag should be 0 (nullable) after migration");
	}

	@Test
	@DisplayName("validateSchema - all existing rows are preserved after table recreation for nullability fix")
	void validateSchema_nullabilityFix_preservesExistingRows() {
		helper.runQueries(db -> {
			table.insertTableQuery(db, new TestEntity("p1", "Alice", 10));
			table.insertTableQuery(db, new TestEntity("p2", "Bob", 20));
			table.insertTableQuery(db, new TestEntity("p3", "Carol", 30));
		});

		helper.runQueries(makeTestEntityTable(true)::validateSchema);

		helper.runQueries(db -> {
			List<Object[]> rows = db.table(TestTable.TABLE_NAME).selectAll();
			assertEquals(3, rows.size(), "All rows must survive table recreation during nullability migration");
		});
	}

	@Test
	@DisplayName("validateSchema - no table recreation when nullability already matches definition")
	void validateSchema_noNullabilityMismatch_isNoOp() {
		helper.runQueries(db -> table.insertTableQuery(db, new TestEntity("o1", "Dave", 7)));

		// same nullability as the live DB — no recreation should occur
		helper.runQueries(table::validateSchema);

		helper.runQueries(db -> {
			List<Object[]> rows = db.table(TestTable.TABLE_NAME).selectAll();
			assertEquals(1, rows.size(), "Row must still exist — no recreation on a matching schema");
			assertEquals("Dave", rows.getFirst()[1]);
		});
	}

	@Test
	@DisplayName("validateSchema - changes NULL column to NOT NULL; PRAGMA confirms notnull=1 after migration")
	void validateSchema_nullableToNotNull_columnRejectsNullAfterMigration() {
		// Start from a nullable-score table
		Table<TestEntity> nullableTable = makeTestEntityTable(true);
		helper.runQueries(db -> {
			db.executeUpdate("DROP TABLE " + TestTable.TABLE_NAME);
			db.table(TestTable.TABLE_NAME).createTable(nullableTable.createTableQuery(db));
			db.table(TestTable.TABLE_NAME)
			  .insert(new String[]{"id", "name", "score"}, new Object[]{"t1", "Eve", 5},
			          new int[]{Types.VARCHAR, Types.VARCHAR, Types.INTEGER});
		});

		// Validate with original definition (score canBeNull=false in TestTable)
		helper.runQueries(table::validateSchema);

		assertEquals(1, queryScoreNotNullFlag(), "score notnull flag should be 1 (NOT NULL) after migration");
	}

	@Test
	@DisplayName(
			"selectAllTableQuery - returns rows in definition order after a new column is ALTER TABLE'd into the middle")
	void selectAllTableQuery_preservesDefinitionColumnOrderAfterAlterTable() {
		// 1. Original table [id, name, score] — insert a row with known values
		helper.runQueries(db -> table.insertTableQuery(db, new TestEntity("ord1", "Alice", 42)));

		// 2. Extended definition places 'bonus' between 'name' and 'score': [id, name, bonus, score]
		Table<TestEntity> extendedTable = new Table<>(TestTable.TABLE_NAME) {
			{
				addAttribute(new Attribute<>("id", true, String.class));
				addAttribute(new Attribute<>("name", false, String.class));
				Attribute<Integer> bonus = new Attribute<>("bonus", false, Integer.class);
				bonus.setCanBeNull(true);
				addAttribute(bonus);
				addAttribute(new Attribute<>("score", false, Integer.class));
			}

			@Override
			public Object[] getData(TestEntity e) {
				return new Object[]{e.getId(), e.getName(), null, e.getScore()};
			}

			@Override
			public Map<String, Object> searchCriteria(TestEntity e) {
				return createSearchCriteria("id = ?", new Object[]{e.getId()}, new int[]{Types.VARCHAR}, new int[]{0});
			}
		};

		// 3. validateSchema adds 'bonus' via ALTER TABLE — SQLite physically appends it at the end.
		//    DB physical order is now [id, name, score, bonus], not [id, name, bonus, score].
		helper.runQueries(extendedTable::validateSchema);

		// 4. Set a known bonus value so a column-swap would produce a clearly wrong result
		helper.runQueries(db ->
								  db.table(TestTable.TABLE_NAME).update(
										  "id = ?", new Object[]{"ord1"}, new int[]{Types.VARCHAR},
						                  new String[]{"bonus"}, new Object[]{99}, new int[]{Types.INTEGER}));

		// 5. selectAllTableQuery must return [id, name, bonus, score] (definition order).
		//    A plain SELECT * would return [id, name, score, bonus] (physical order), making row[2]=42 and row[3]=99 —
		//    the exact swap that breaks repository deserialization when a column is added mid-definition.
		helper.runQueries(db -> {
			List<Object[]> rows = extendedTable.selectAllTableQuery(db);
			assertEquals(1, rows.size());
			Object[] row = rows.getFirst();
			assertEquals("ord1", row[0], "row[0] must be id");
			assertEquals("Alice", row[1], "row[1] must be name");
			assertEquals(99, ((Number) row[2]).intValue(), "row[2] must be bonus (definition order), not score");
			assertEquals(42, ((Number) row[3]).intValue(), "row[3] must be score (definition order), not bonus");
		});
	}

	@Test
	@DisplayName("getColumns - returns the attribute names defined in addAttribute()")
	void getColumns_returnsAttributeNames() {
		Set<String> cols = table.getColumns();
		assertTrue(cols.contains("id"), "Expected 'id'");
		assertTrue(cols.contains("name"), "Expected 'name'");
		assertTrue(cols.contains("score"), "Expected 'score'");
		assertEquals(3, cols.size());
	}

	@Test
	@DisplayName("getAttributes - returns an unmodifiable view of the attribute map")
	void getAttributes_returnsUnmodifiableMap() {
		Map<String, Attribute<?>> attrs = table.getAttributes();
		assertThrows(UnsupportedOperationException.class,
		             () -> attrs.put("injected", new Attribute<>("x", false, String.class)));
	}

	@Test
	@DisplayName("get(column) - retrieves the attribute by lowercase key")
	void get_caseInsensitive() {
		assertNotNull(table.get("ID"), "get('ID') should find attribute 'id'");
		assertNotNull(table.get("Name"), "get('Name') should find attribute 'name'");
		assertNotNull(table.get("SCORE"), "get('SCORE') should find attribute 'score'");
	}

	/**
	 * Reads the SQLite {@code notnull} flag for the {@code score} column of {@code test_entity} via
	 * {@code PRAGMA table_info}. Returns {@code 1} if the column is NOT NULL, {@code 0} if nullable, or {@code -1} if
	 * the column was not found. This bypasses exception-swallowing in {@link DatabaseHelper#runQueries} and gives a
	 * direct view of the live schema constraint.
	 */
	private int queryScoreNotNullFlag() {
		int[] result = {-1};
		helper.runQueries(db -> {
			try (PreparedStatement stmt = db.getConnection()
			                                .prepareStatement("PRAGMA table_info(" + TestTable.TABLE_NAME + ")");
			     ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					if ("score".equals(rs.getString("name"))) {
						result[0] = rs.getInt("notnull");
						return;
					}
				}
			}
		});
		return result[0];
	}

	/**
	 * Builds a {@link Table} for {@code test_entity} that is identical to {@link TestTable} except that the
	 * {@code score} column's nullability is controlled by {@code scoreNullable}.
	 */
	private Table<TestEntity> makeTestEntityTable(boolean scoreNullable) {
		return new Table<>(TestTable.TABLE_NAME) {
			{
				Attribute<String>  id    = new Attribute<>("id", true, String.class);
				Attribute<String>  name  = new Attribute<>("name", false, String.class);
				Attribute<Integer> score = new Attribute<>("score", false, Integer.class);
				id.setCanBeNull(false);
				name.setCanBeNull(false);
				score.setCanBeNull(scoreNullable);
				addAttribute(id);
				addAttribute(name);
				addAttribute(score);
			}

			@Override
			public Object[] getData(TestEntity e) {
				return new Object[]{e.getId(), e.getName(), e.getScore()};
			}

			@Override
			public Map<String, Object> searchCriteria(TestEntity e) {
				return createSearchCriteria("id = ?", new Object[]{e.getId()}, new int[]{Types.VARCHAR}, new int[]{0});
			}
		};
	}
}
