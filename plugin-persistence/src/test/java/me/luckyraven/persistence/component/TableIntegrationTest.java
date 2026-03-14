package me.luckyraven.persistence.component;

import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.support.MockPluginFactory;
import me.luckyraven.persistence.support.TestDatabaseHandler;
import me.luckyraven.persistence.support.TestEntity;
import me.luckyraven.persistence.support.TestTable;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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

	// ─────────────────────────────────────────── Column metadata ──

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
}
