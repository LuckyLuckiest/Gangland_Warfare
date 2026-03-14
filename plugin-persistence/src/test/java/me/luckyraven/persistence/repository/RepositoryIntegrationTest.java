package me.luckyraven.persistence.repository;

import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.support.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link AbstractRepository} / {@link IRepository} contract.
 *
 * <p>All tests use a real SQLite database.  {@code plugin.isEnabled()} returns {@code false}
 * so every {@code runQueriesAsync()} call degrades gracefully to a synchronous {@code runQueries()} - no Bukkit
 * scheduler needed.
 */
@DisplayName("AbstractRepository - IRepository contract")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class RepositoryIntegrationTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private JavaPlugin      plugin;
	private DatabaseHandler handler;
	private TestRepository  repo;
	private DatabaseHelper  helper;

	@BeforeEach
	void setUp() throws Exception {
		plugin  = MockPluginFactory.plugin(tempDir);
		handler = new TestDatabaseHandler(plugin, MockPluginFactory.sqliteOnly(), "repo_test");
		handler.setType(DatabaseHandler.SQLITE);
		handler.initialize();

		helper = new DatabaseHelper(plugin, handler);

		// Create the physical table before each test.
		TestTable table = new TestTable();
		helper.runQueries(db -> db.table(table.getName()).createTable(table.createTableQuery(db)));

		repo = new TestRepository(plugin, handler);
	}

	@AfterEach
	void tearDown() {
		Database db = handler.getDatabase();
		if (db != null && db.getConnection() != null) db.disconnect();
		// Windows: release SQLite file handles before JUnit deletes @TempDir
		MockPluginFactory.releaseDbFiles(tempDir);
	}

	// ────────────────────────────────────────────────── loadAll ──

	@Test
	@DisplayName("loadAll - returns empty collection when the table has no rows")
	void loadAll_emptyTable_returnsEmptyCollection() {
		Collection<TestEntity> all = repo.loadAll();
		assertTrue(all.isEmpty());
	}

	@Test
	@DisplayName("loadAll - returns all rows that were inserted directly")
	void loadAll_afterDirectInsert_returnsRows() throws Exception {
		TestEntity alice = new TestEntity("a1", "Alice", 100);
		TestEntity bob   = new TestEntity("b1", "Bob", 50);

		helper.runQueries(db -> repo.getTablePublic().insertTableQuery(db, alice));
		helper.runQueries(db -> repo.getTablePublic().insertTableQuery(db, bob));

		Collection<TestEntity> all = repo.loadAll();
		assertEquals(2, all.size());
		assertTrue(all.contains(alice));
		assertTrue(all.contains(bob));
	}

	// ────────────────────────────────────────────────────── save ──

	@Test
	@DisplayName("save - inserts a new entity when the row does not exist")
	void save_newEntity_insertsRow() {
		TestEntity entity = new TestEntity("s1", "Sal", 200);

		repo.save(entity);  // sync because plugin.isEnabled()=false

		Collection<TestEntity> all = repo.loadAll();
		assertEquals(1, all.size());
		assertTrue(all.contains(entity));
	}

	@Test
	@DisplayName("save - updates the existing row when the primary key already exists")
	void save_existingEntity_updatesRow() {
		TestEntity original = new TestEntity("s2", "Tom", 10);
		TestEntity updated  = new TestEntity("s2", "Tommy", 99);

		repo.save(original);
		repo.save(updated);

		Collection<TestEntity> all = repo.loadAll();
		assertEquals(1, all.size(), "There should still be only one row");
		TestEntity loaded = all.iterator().next();
		assertEquals("Tommy", loaded.getName(), "name should have been updated");
		assertEquals(99, loaded.getScore(), "score should have been updated");
	}

	@Test
	@DisplayName("save - multiple distinct entities are all persisted")
	void save_multipleEntities_allPersisted() {
		List<TestEntity> entities = List.of(new TestEntity("m1", "Eve", 1), new TestEntity("m2", "Frank", 2),
											new TestEntity("m3", "Grace", 3));

		entities.forEach(repo::save);

		assertEquals(3, repo.loadAll().size());
	}

	// ──────────────────────────────────────────────────── saveAll ──

	@Test
	@DisplayName("saveAll - persists every entity in the supplied collection")
	void saveAll_persistsAll() {
		List<TestEntity> batch = List.of(new TestEntity("b1", "Han", 10), new TestEntity("b2", "Ivy", 20),
										 new TestEntity("b3", "Jake", 30));

		repo.saveAll(batch);

		assertEquals(3, repo.loadAll().size());
	}

	@Test
	@DisplayName("saveAll - empty collection does not throw")
	void saveAll_emptyCollection_doesNotThrow() {
		assertDoesNotThrow(() -> repo.saveAll(List.of()));
	}

	// ────────────────────────────────────────── saveAllFromMemory ──

	@Test
	@DisplayName("saveAllFromMemory - throws IllegalStateException when no supplier is set")
	void saveAllFromMemory_withoutSupplier_throwsIllegalState() {
		assertThrows(IllegalStateException.class, () -> repo.saveAllFromMemory());
	}

	@Test
	@DisplayName("saveAllFromMemory - persists all entities provided by the supplier")
	void saveAllFromMemory_withSupplier_savesAll() {
		List<TestEntity> inMemory = List.of(new TestEntity("sup1", "Kim", 5), new TestEntity("sup2", "Leo", 6));
		repo.setDataSupplier(() -> inMemory);

		repo.saveAllFromMemory();

		assertEquals(2, repo.loadAll().size());
	}

	// ────────────────────────────────────────────────── delete ──

	@Test
	@DisplayName("delete - removes the entity from the database")
	void delete_removesRow() {
		TestEntity entity = new TestEntity("d1", "Mia", 7);
		repo.save(entity);
		assertEquals(1, repo.loadAll().size());

		repo.delete(entity);

		assertTrue(repo.loadAll().isEmpty(), "Row should be removed after delete()");
	}

	@Test
	@DisplayName("delete - deleting a non-existent entity does not throw")
	void delete_nonExistentEntity_doesNotThrow() {
		assertDoesNotThrow(() -> repo.delete(new TestEntity("ghost", "Nobody", 0)));
	}

	// ──────────────────────────────────────── isRowAvailable helper ──

	@Test
	@DisplayName("isRowAvailable - returns true when the row is present")
	void isRowAvailable_existingRow_returnsTrue() throws Exception {
		TestEntity entity = new TestEntity("r1", "Nina", 8);
		helper.runQueries(db -> repo.getTablePublic().insertTableQuery(db, entity));

		// Access via package-private method - both are in the repository sub-package
		boolean[] result = {false};
		helper.runQueries(db -> result[0] = repo.isRowAvailable(entity, db));
		assertTrue(result[0]);
	}

	@Test
	@DisplayName("isRowAvailable - returns false for a row that was never inserted")
	void isRowAvailable_missingRow_returnsFalse() throws Exception {
		boolean[] result = {true};
		helper.runQueries(db -> result[0] = repo.isRowAvailable(new TestEntity("missing", "Nobody", 0), db));
		assertFalse(result[0]);
	}

	// ─────────────────────────────────────── accessor plumbing ──

	@Test
	@DisplayName("getDatabaseHandler - returns the handler passed to the constructor")
	void getDatabaseHandler_returnsInjectedHandler() {
		assertSame(handler, repo.getDatabaseHandler());
	}

	@Test
	@DisplayName("getDatabase - returns the live Database from the handler")
	void getDatabase_returnsHandlerDatabase() {
		assertSame(handler.getDatabase(), repo.getDatabase());
	}
}
