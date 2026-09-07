package org.luckyraven.gangland.database.repositories.copsncrooks;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.luckyraven.gangland.database.tables.copsncrooks.DetainmentTable;
import org.luckyraven.gangland.database.tables.copsncrooks.JailTable;
import org.luckyraven.keystone.persistence.database.Database;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.SchemaMigrations;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the {@code CJ-01} schema migration (issue #33, cops-detainment-jail.md Observation #1). Removing
 * {@code setUnique(true)} from {@link DetainmentTable} only fixes <em>fresh</em> installs: Keystone's
 * {@code SchemaDiff} compares column names only, so a deployed {@code detainment} table keeps its legacy
 * {@code UNIQUE(jail_id)} forever and the second inmate of a cell is still never persisted.
 * {@link DetainmentRepository#migrateSchema()} — invoked once per startup by {@code RepositoryRegistry} — rebuilds
 * the table without the constraint.
 *
 * <p>Each test builds a <b>legacy</b> table by hand on a raw JDBC connection (rather than through
 * {@code TableSchemas}, which now emits the fixed shape), then drives the migration through a mocked
 * {@link DatabaseHandler} wired to that same connection — the shape {@code RepositoryRegistry} produces at runtime.
 *
 * <p>Uses a disabled {@code PluginMocks} plugin so every repository write takes the synchronous inline path.
 */
@DisplayName("DetainmentRepository.migrateSchema — dropping the legacy UNIQUE(jail_id)")
class DetainmentRepositoryMigrationTest {

	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
	private static final UUID BOB   = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");
	private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-00000000ca01");

	private static final String LEGACY_DETAINMENT_DDL =
			"CREATE TABLE detainment (" +
			"player_uuid VARCHAR(36) NOT NULL, " +
			"jail_id INTEGER UNIQUE, " +
			"state VARCHAR(255) NOT NULL, " +
			"transit_expires_at BIGINT, " +
			"sentence_expires_at BIGINT, " +
			"wanted_at_arrest INTEGER, " +
			"PRIMARY KEY (player_uuid), " +
			"FOREIGN KEY (jail_id) REFERENCES jail(id))";

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: SQLite holds the .db handle past the test
	Path tempDir;

	private Connection           legacyConnection;
	private SqliteBackend        backend;
	private DetainmentRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		Path dbFile = tempDir.resolve("detainment-migration.db");

		legacyConnection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(dbFile));
		backend.applySchema(TableSchemas.fromTable(new JailTable()));

		Database database = mock(Database.class);
		when(database.getConnection()).thenReturn(legacyConnection);

		DatabaseHandler handler = mock(DatabaseHandler.class);
		when(handler.getType()).thenReturn(DatabaseHandler.SQLITE);
		when(handler.getDatabase()).thenReturn(database);

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		repository = new DetainmentRepository(plugin, handler, backend);
	}

	@AfterEach
	void tearDown() throws SQLException {
		backend.disconnect();
		if (legacyConnection != null) legacyConnection.close();
		DbFiles.release(tempDir);
	}

	private void createLegacyTable() throws SQLException {
		try (Statement stmt = legacyConnection.createStatement()) {
			stmt.execute(LEGACY_DETAINMENT_DDL);
		}
	}

	private void insertLegacyRow(UUID playerId, int jailId, long sentenceExpiresAt, int wantedAtArrest)
			throws SQLException {
		try (Statement stmt = legacyConnection.createStatement()) {
			stmt.execute("INSERT INTO detainment " +
			             "(player_uuid, jail_id, state, transit_expires_at, sentence_expires_at, wanted_at_arrest) " +
			             "VALUES ('" + playerId + "', " + jailId + ", 'JAILED', NULL, " + sentenceExpiresAt + ", " +
			             wantedAtArrest + ")");
		}
	}

	private boolean jailIdStillUnique() throws SQLException {
		return SchemaMigrations.hasUniqueIndex(legacyConnection, DatabaseHandler.SQLITE, "detainment", "jail_id");
	}

	@Test
	@DisplayName("the legacy table really does reject a second inmate — the defect this migration exists for")
	void legacyTable_rejectsSecondInmateInTheSameCell() throws SQLException {
		createLegacyTable();
		insertLegacyRow(ALICE, 1, 5_000L, 3);

		assertTrue(jailIdStillUnique(), "fixture sanity: the legacy table must carry UNIQUE(jail_id)");
		assertThrows(SQLException.class, () -> insertLegacyRow(BOB, 1, 6_000L, 2),
		             "the legacy UNIQUE(jail_id) rejects the second inmate of a cell");
	}

	@Test
	@DisplayName("after migrating a legacy table, two inmates share one cell and both persist")
	void migrateSchema_legacyTable_twoInmatesInOneCellPersist() throws SQLException {
		createLegacyTable();

		repository.migrateSchema();

		assertFalse(jailIdStillUnique(), "the migration must drop UNIQUE(jail_id)");

		repository.save(new DetainedPlayer(ALICE, 1, DetainmentState.JAILED));
		repository.save(new DetainedPlayer(BOB, 1, DetainmentState.JAILED));

		Collection<DetainedPlayer> loaded = repository.loadAll();

		assertEquals(2, loaded.size(), "the second inmate of cell 1 must be persisted too");
		assertEquals(2, loaded.stream().filter(row -> Integer.valueOf(1).equals(row.getJailId())).count());
	}

	@Test
	@DisplayName("existing rows survive the rebuild with every column intact")
	void migrateSchema_legacyTable_preservesExistingRows() throws SQLException {
		createLegacyTable();
		insertLegacyRow(ALICE, 1, 5_000L, 3);

		repository.migrateSchema();

		DetainedPlayer loaded = repository.loadAll().iterator().next();

		assertEquals(ALICE, loaded.getPlayerId());
		assertEquals(1, loaded.getJailId());
		assertEquals(DetainmentState.JAILED, loaded.getState());
		assertEquals(5_000L, loaded.getSentenceExpiresAt());
		assertEquals(3, loaded.getWantedAtArrest());
	}

	@Test
	@DisplayName("running the migration twice is harmless — it is safe on every startup")
	void migrateSchema_runTwice_isIdempotent() throws SQLException {
		createLegacyTable();
		insertLegacyRow(ALICE, 1, 5_000L, 3);

		repository.migrateSchema();
		assertDoesNotThrow(() -> repository.migrateSchema());

		assertFalse(jailIdStillUnique());

		repository.save(new DetainedPlayer(BOB, 1, DetainmentState.JAILED));
		repository.save(new DetainedPlayer(CAROL, 1, DetainmentState.JAILED));

		assertEquals(3, repository.loadAll().size(), "the pre-existing row plus two new cellmates");
	}

	@Test
	@DisplayName("a fresh install built from the fixed table declaration is left untouched")
	void migrateSchema_freshSchema_isNoOp() throws SQLException {
		backend.applySchema(TableSchemas.fromTable(new DetainmentTable(new JailTable())));

		assertFalse(jailIdStillUnique(), "the fixed DetainmentTable must not emit UNIQUE(jail_id)");
		assertDoesNotThrow(() -> repository.migrateSchema());

		repository.save(new DetainedPlayer(ALICE, 1, DetainmentState.JAILED));
		repository.save(new DetainedPlayer(BOB, 1, DetainmentState.JAILED));

		assertEquals(2, repository.loadAll().size());
	}

	@Test
	@DisplayName("no connection means no migration attempt")
	void migrateSchema_noConnection_returnsQuietly() {
		DatabaseHandler handler = mock(DatabaseHandler.class);
		when(handler.getType()).thenReturn(DatabaseHandler.SQLITE);

		DetainmentRepository disconnected =
				new DetainmentRepository(PluginMocks.plugin(tempDir), handler, backend);

		assertDoesNotThrow(disconnected::migrateSchema);
	}
}
