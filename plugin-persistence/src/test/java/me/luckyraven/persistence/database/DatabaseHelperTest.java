package me.luckyraven.persistence.database;

import me.luckyraven.persistence.database.type.SQLite;
import me.luckyraven.persistence.support.MockPluginFactory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DatabaseHelper}.
 *
 * <p>These tests verify the synchronous {@code runQueries()} contract using a real in-process
 * SQLite connection - no Bukkit server or scheduler is required.
 *
 * <p>The {@code runQueriesAsync()} method delegates to {@code runQueries()} whenever
 * {@code plugin.isEnabled()} returns {@code false} (plugin disabled / shutdown path), which we exploit here to test the
 * async-path fallback without touching the Bukkit scheduler. For tests that exercise the {@code isEnabled() == true}
 * branch (where {@link org.bukkit.Bukkit#getScheduler()} is actually invoked), set up MockBukkit - see
 * {@code TESTING.md} for step-by-step instructions.
 */
@DisplayName("DatabaseHelper - query execution")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class DatabaseHelperTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private SQLite          realDb;
	private DatabaseHandler mockHandler;
	private JavaPlugin      mockPlugin;
	private DatabaseHelper  helper;

	@BeforeEach
	void setUp() throws SQLException {
		mockPlugin = mock(JavaPlugin.class);
		when(mockPlugin.getDataFolder()).thenReturn(tempDir.toFile());

		realDb = new SQLite(mockPlugin);
		realDb.initialize(Collections.emptyMap(), tempDir.resolve("helper_test.db").toString());
		realDb.connect();

		// Prepare a reusable test table.
		realDb.table("test").createTable("id INTEGER PRIMARY KEY", "value VARCHAR(255)");

		mockHandler = mock(DatabaseHandler.class);
		when(mockHandler.getDatabase()).thenReturn(realDb);

		helper = new DatabaseHelper(mockPlugin, mockHandler);
	}

	@AfterEach
	void tearDown() {
		try {
			if (realDb.getConnection() != null) realDb.disconnect();
		} catch (Exception ignored) { }
		// Windows: release SQLite file handles before JUnit deletes @TempDir
		MockPluginFactory.releaseDbFiles(tempDir);
	}

	// ────────────────────────────────────────────── runQueries (sync) ──

	@Test
	@DisplayName("runQueries - executes the lambda and persists changes to the database")
	void runQueries_executesQueryAndPersistsData() throws SQLException {
		helper.runQueries(db -> db.table("test")
								  .insert(new String[]{"id", "value"}, new Object[]{1, "hello"},
										  new int[]{Types.INTEGER, Types.VARCHAR}));

		assertEquals(1, realDb.table("test").totalRows(), "Inserted row must be visible after runQueries() completes");
	}

	@Test
	@DisplayName("runQueries - lambda is actually invoked")
	void runQueries_lambdaIsInvoked() {
		AtomicBoolean invoked = new AtomicBoolean(false);

		helper.runQueries(db -> invoked.set(true));

		assertTrue(invoked.get(), "Lambda must be executed");
	}

	@Test
	@DisplayName("runQueries - swallows SQLException without propagating it to the caller")
	void runQueries_sqlException_doesNotPropagateToCaller() {
		assertDoesNotThrow(() -> helper.runQueries(db -> {
			throw new SQLException("Intentional test exception");
		}));
	}

	@Test
	@DisplayName("runQueries - swallows generic Throwable without propagating it to the caller")
	void runQueries_runtimeException_doesNotPropagate() {
		assertDoesNotThrow(() -> helper.runQueries(db -> {
			throw new RuntimeException("Intentional runtime exception");
		}));
	}

	@Test
	@DisplayName("runQueries - skips execution entirely when the database is null")
	void runQueries_nullDatabase_skipsLambda() {
		when(mockHandler.getDatabase()).thenReturn(null);
		DatabaseHelper helperWithNullDb = new DatabaseHelper(mockPlugin, mockHandler);

		AtomicBoolean invoked = new AtomicBoolean(false);
		assertDoesNotThrow(() -> helperWithNullDb.runQueries(db -> invoked.set(true)));
		assertFalse(invoked.get(), "Lambda must NOT run when database is null");
	}

	@Test
	@DisplayName("runQueries - all statements inside a single lambda are committed in order")
	void runQueries_multipleStatementsInOneLambda_allPersisted() throws SQLException {
		helper.runQueries(db -> {
			db.table("test")
			  .insert(new String[]{"id", "value"}, new Object[]{10, "first"}, new int[]{Types.INTEGER, Types.VARCHAR});
			db.table("test")
			  .insert(new String[]{"id", "value"}, new Object[]{20, "second"}, new int[]{Types.INTEGER, Types.VARCHAR});
			db.table("test")
			  .insert(new String[]{"id", "value"}, new Object[]{30, "third"}, new int[]{Types.INTEGER, Types.VARCHAR});
		});

		assertEquals(3, realDb.table("test").totalRows());
	}

	@Test
	@DisplayName("runQueries - consecutive calls each execute their own lambda")
	void runQueries_consecutiveCalls_eachLambdaExecuted() {
		AtomicInteger counter = new AtomicInteger(0);

		helper.runQueries(db -> counter.incrementAndGet());
		helper.runQueries(db -> counter.incrementAndGet());
		helper.runQueries(db -> counter.incrementAndGet());

		assertEquals(3, counter.get());
	}

	// ──────────────────────────────────────── runQueriesAsync (sync fallback) ──

	@Test
	@DisplayName("runQueriesAsync - falls back to synchronous execution when plugin is disabled")
	void runQueriesAsync_pluginDisabled_runsSynchronously() {
		// When isEnabled() == false, runQueriesAsync() calls runQueries() directly
		// (no Bukkit scheduler interaction needed).
		when(mockPlugin.isEnabled()).thenReturn(false);

		AtomicBoolean invoked = new AtomicBoolean(false);
		helper.runQueriesAsync(db -> invoked.set(true));

		assertTrue(invoked.get(), "Lambda must execute synchronously when the plugin is in disabled state");
	}

	// ──────────────────────────────────────────────────── Accessors ──

	@Test
	@DisplayName("getDatabaseHandler - returns the handler injected via constructor")
	void getDatabaseHandler_returnsInjectedHandler() {
		assertSame(mockHandler, helper.getDatabaseHandler());
	}
}
