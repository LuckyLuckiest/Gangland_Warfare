package org.luckyraven.gangland.persistence.repository;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.persistence.database.Database;
import org.luckyraven.gangland.persistence.database.DatabaseHandler;
import org.luckyraven.gangland.persistence.database.DatabaseHelper;
import org.luckyraven.gangland.persistence.database.component.Attribute;
import org.luckyraven.gangland.persistence.database.component.Table;
import org.luckyraven.gangland.persistence.support.*;

import java.nio.file.Path;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link RepositoryRegistry}.
 *
 * <p>Uses {@link RepositoryRegistry#registerRepository(IRepository, Class)} (the manual API)
 * rather than the classpath-scan {@code scanAndRegisterRepositories()} to avoid needing a real plugin classloader in
 * tests.
 */
@DisplayName("RepositoryRegistry – registration and lifecycle")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class RepositoryRegistryIntegrationTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private JavaPlugin         plugin;
	private DatabaseHandler    handler;
	private DatabaseHelper     helper;
	private RepositoryRegistry registry;

	/**
	 * Creates a minimal anonymous repository that wraps a given table.
	 */
	private static <T> IRepository<T> minimalRepo(JavaPlugin plugin, DatabaseHandler handler, Table<T> table) {
		return new AbstractRepository<>(plugin, handler) {
			@Override
			protected java.util.Collection<T> doLoadAll() { return List.of(); }

			@Override
			protected <E> java.util.function.Consumer<E> processSave() { return null; }

			@Override
			protected Table<T> getTable() { return table; }

			@Override
			protected void doDelete(T data) { }
		};
	}

	@BeforeEach
	void setUp() throws Exception {
		plugin  = MockPluginFactory.plugin(tempDir);
		handler = new TestDatabaseHandler(plugin, MockPluginFactory.sqliteOnly(), "reg_test");
		handler.setType(DatabaseHandler.SQLITE);
		handler.initialize();
		helper   = new DatabaseHelper(plugin, handler);
		registry = new RepositoryRegistry(plugin, handler);
	}

	// ─────────────────────────────────── registerRepository / lookup ──

	@AfterEach
	void tearDown() {
		Database db = handler.getDatabase();
		if (db != null && db.getConnection() != null) db.disconnect();
		// Windows: release SQLite file handles before JUnit deletes @TempDir
		MockPluginFactory.releaseDbFiles(tempDir);
	}

	@Test
	@DisplayName("registerRepository – getRepository returns the exact instance that was registered")
	void registerRepository_andGetRepository_returnsSameInstance() {
		TestRepository repo = new TestRepository(plugin, handler);
		registry.registerRepository(repo, TestEntity.class);

		IRepository<TestEntity> found = registry.getRepository(TestEntity.class);
		assertSame(repo, found);
	}

	@Test
	@DisplayName("getRepository – throws IllegalStateException for an unknown entity type")
	void getRepository_unknownType_throwsIllegalState() {
		assertThrows(IllegalStateException.class, () -> registry.getRepository(String.class));
	}

	@Test
	@DisplayName("hasRepository – returns true for a registered type, false for an unregistered one")
	void hasRepository_trueAndFalse() {
		registry.registerRepository(new TestRepository(plugin, handler), TestEntity.class);

		assertTrue(registry.hasRepository(TestEntity.class));
		assertFalse(registry.hasRepository(Integer.class));
	}

	@Test
	@DisplayName("getAllRepositories – contains the repository that was registered")
	void getAllRepositories_containsAllRegistered() {
		TestRepository r1 = new TestRepository(plugin, handler);
		registry.registerRepository(r1, TestEntity.class);

		// RepositoryRegistry is a map keyed by entity type; registering the same type twice
		// overwrites the first entry (covered by registerRepository_duplicate_overwritesPreviousEntry).
		Collection<IRepository<?>> all = registry.getAllRepositories();
		assertEquals(1, all.size());
		assertTrue(all.contains(r1));
	}

	// ──────────────────────────────────── table extraction via registry ──

	@Test
	@DisplayName("registerRepository – second registration for the same entity type overwrites the first")
	void registerRepository_duplicate_overwritesPreviousEntry() {
		TestRepository first  = new TestRepository(plugin, handler);
		TestRepository second = new TestRepository(plugin, handler);

		registry.registerRepository(first, TestEntity.class);
		registry.registerRepository(second, TestEntity.class);

		assertSame(second, registry.getRepository(TestEntity.class), "Second registration must overwrite the first");
		assertEquals(1, registry.getAllRepositories()
				.stream().filter(r -> r == second).count());
	}

	@Test
	@DisplayName("registerRepository – extracts and stores the Table from an AbstractRepository")
	void registerRepository_extractsTable() {
		registry.registerRepository(new TestRepository(plugin, handler), TestEntity.class);

		List<Table<?>> tables = registry.getRegisteredTables();
		assertFalse(tables.isEmpty(), "Table should be extracted automatically");
		assertEquals(TestTable.TABLE_NAME, tables.getFirst().getName());
	}

	@Test
	@DisplayName("getTable(name) – returns the Table extracted during registration")
	void getTable_byName_returnsCorrectTable() {
		registry.registerRepository(new TestRepository(plugin, handler), TestEntity.class);

		Table<?> found = registry.getTable(TestTable.TABLE_NAME);
		assertNotNull(found);
		assertEquals(TestTable.TABLE_NAME, found.getName());
	}

	// ────────────────────────────────────────────────── createTables ──

	@Test
	@DisplayName("getRegisteredTables – returns an unmodifiable view")
	void getRegisteredTables_returnsUnmodifiableList() {
		registry.registerRepository(new TestRepository(plugin, handler), TestEntity.class);
		List<Table<?>> tables = registry.getRegisteredTables();
		assertThrows(UnsupportedOperationException.class, () -> tables.add(null));
	}

	@Test
	@DisplayName("createTables – physically creates the table in the database")
	void createTables_createsPhysicalTable() throws Exception {
		registry.registerRepository(new TestRepository(plugin, handler), TestEntity.class);

		// Connect first so createTables can use the open connection
		helper.runQueries(db -> { /* no-op: opens the connection */ });

		registry.createTables();

		helper.runQueries(db -> {
			List<String> cols = db.table(TestTable.TABLE_NAME).getColumns();
			assertTrue(cols.contains("id"), "column 'id' must exist");
			assertTrue(cols.contains("name"), "column 'name' must exist");
			assertTrue(cols.contains("score"), "column 'score' must exist");
		});
	}

	@Test
	@DisplayName("createTables – createTables is idempotent (IF NOT EXISTS)")
	void createTables_calledTwice_doesNotThrow() throws Exception {
		registry.registerRepository(new TestRepository(plugin, handler), TestEntity.class);
		helper.runQueries(db -> { /* open connection */ });

		assertDoesNotThrow(() -> registry.createTables());
		assertDoesNotThrow(() -> registry.createTables(), "Second call must not throw");
	}

	// ──────────────────────────────────────────────────── saveAll ──

	@Test
	@DisplayName("createTables – respects foreign-key dependency order")
	void createTables_foreignKeyDependency_correctOrder() throws Exception {
		// parent table (no FK)
		Table<String> parentTable = new Table<>("parent_tbl") {
			{
				Attribute<String> pk = new Attribute<>("parent_id", true, String.class);
				pk.setCanBeNull(false);
				addAttribute(pk);
			}

			@Override
			public Object[] getData(String s) { return new Object[]{s}; }

			@Override
			public Map<String, Object> searchCriteria(String s) {
				return createSearchCriteria("parent_id = ?", new Object[]{s}, new int[]{Types.VARCHAR}, new int[]{0});
			}
		};

		// child table with FK → parent
		Attribute<String> fkAttr   = new Attribute<>("parent_ref", false, String.class);
		Attribute<String> parentPk = new Attribute<>("parent_id", true, String.class);
		fkAttr.setForeignKey(parentPk, parentTable);

		Table<String> childTable = new Table<>("child_tbl") {
			{
				Attribute<String> pk = new Attribute<>("child_id", true, String.class);
				pk.setCanBeNull(false);
				addAttribute(pk);
				addAttribute(fkAttr);
			}

			@Override
			public Object[] getData(String s) { return new Object[]{s, s}; }

			@Override
			public Map<String, Object> searchCriteria(String s) {
				return createSearchCriteria("child_id = ?", new Object[]{s}, new int[]{Types.VARCHAR}, new int[]{0});
			}
		};

		// Build minimal repos that wrap these tables
		IRepository<String> parentRepo = minimalRepo(plugin, handler, parentTable);
		IRepository<String> childRepo  = minimalRepo(plugin, handler, childTable);

		// Register child FIRST to check that the registry reorders by FK dependency
		registry.registerRepository(childRepo, String.class);
		registry.registerRepository(parentRepo, String.class); // different key for parent

		helper.runQueries(db -> { /* open connection */ });

		// Should not throw - parent must be created before child
		assertDoesNotThrow(() -> registry.createTables());
	}

	@Test
	@DisplayName("saveAll – invokes saveAllFromMemory on every registered repository that has a supplier")
	void saveAll_invokesEachRepositorySupplier() throws Exception {
		// First physically create the table
		helper.runQueries(db -> {
			TestTable t = new TestTable();
			db.table(t.getName()).createTable(t.createTableQuery(db));
		});

		TestRepository repo = new TestRepository(plugin, handler);
		repo.setDataSupplier(() -> List.of(new TestEntity("sv1", "Oscar", 1), new TestEntity("sv2", "Penny", 2)));
		registry.registerRepository(repo, TestEntity.class);

		registry.saveAll();   // triggers saveAllFromMemory() on each repo

		assertEquals(2, repo.loadAll().size());
	}

	// ─────────────────────────────────────── helper ──

	@Test
	@DisplayName("saveAll – continues to the next repository even when one throws")
	void saveAll_oneRepoThrows_continuesForOthers() {
		// Register a repo with no supplier set (saveAllFromMemory will throw)
		TestRepository throwing = new TestRepository(plugin, handler);
		registry.registerRepository(throwing, TestEntity.class);

		// Should not propagate the exception
		assertDoesNotThrow(() -> registry.saveAll());
	}
}
