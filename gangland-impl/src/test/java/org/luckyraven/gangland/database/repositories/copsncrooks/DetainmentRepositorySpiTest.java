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
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * Pins two P0 detainment-persistence fixes against real SQLite through the DatabaseBackend SPI:
 *
 * <ul>
 *   <li>{@code CJ-01} (issue #33, cops-detainment-jail.md Observation #1) — {@code detainment.jail_id} used to be
 *       declared UNIQUE, so the second inmate of a multi-capacity cell could never be persisted.</li>
 *   <li>{@code CJ-12} (issue #36, Observation #12) — an unrecognised {@code state} string incremented the column
 *       cursor twice, shifting {@code transit_expires_at}, {@code sentence_expires_at} and {@code wanted_at_arrest}
 *       one position to the left.</li>
 * </ul>
 *
 * <p>Uses a disabled {@code PluginMocks} plugin so every repository write takes the synchronous inline path.
 */
@DisplayName("DetainmentRepository — multi-inmate cells and unknown-state column alignment")
class DetainmentRepositorySpiTest {

	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
	private static final UUID BOB   = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari/SQLite holds the .db handle past the test
	Path tempDir;

	private SqliteBackend        backend;
	private DetainmentRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("detainment.db")));
		backend.applySchema(TableSchemas.fromTable(new JailTable()));
		backend.applySchema(TableSchemas.fromTable(new DetainmentTable(new JailTable())));

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		repository = new DetainmentRepository(plugin, mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("CJ-01: jail_id is not declared UNIQUE — a cell holds Jail.Max_Capacity inmates")
	void jailIdAttribute_isNotUnique() {
		Attribute<?> jailId = new DetainmentTable(new JailTable()).get("jail_id");

		assertFalse(jailId.isUnique(),
		            "UNIQUE on jail_id caps every cell at one inmate; uniqueness belongs to player_uuid");
	}

	@Test
	@DisplayName("CJ-01: two inmates sharing one cell both round-trip through the table")
	void twoInmatesInOneCell_bothPersist() {
		repository.save(new DetainedPlayer(ALICE, 1, DetainmentState.JAILED));
		repository.save(new DetainedPlayer(BOB, 1, DetainmentState.JAILED));

		Collection<DetainedPlayer> loaded = repository.loadAll();

		assertEquals(2, loaded.size(), "the second inmate of cell 1 must be persisted too");
		assertEquals(2, loaded.stream().filter(detained -> Integer.valueOf(1).equals(detained.getJailId())).count());
	}

	@Test
	@DisplayName("CJ-12: an unknown state falls back to JAILED without shifting the following columns")
	void unknownState_keepsFollowingColumnsAligned() throws SQLException {
		repository.save(new DetainedPlayer(ALICE, 1, DetainmentState.JAILED, null, 5_000L, 3));

		// Simulate a row written by an older/newer build (or hand-edited) whose state no longer parses.
		backend.execute("UPDATE detainment SET state = ? WHERE player_uuid = ?", "MUTINY", ALICE.toString());

		DetainedPlayer loaded = repository.loadAll().iterator().next();

		assertEquals(DetainmentState.JAILED, loaded.getState(), "unparseable state falls back to JAILED");
		assertNull(loaded.getTransitExpiresAt(), "transit_expires_at must not be read from sentence_expires_at");
		assertEquals(5_000L, loaded.getSentenceExpiresAt(), "sentence_expires_at must not be read from wanted_at_arrest");
		assertEquals(3, loaded.getWantedAtArrest(), "wanted_at_arrest must not fall off the end of the row");
	}

	@Test
	@DisplayName("a well-formed row still round-trips every nullable column")
	void knownState_roundTripsEveryColumn() {
		repository.save(new DetainedPlayer(BOB, 2, DetainmentState.HANDCUFFED, 1_234L, null, 4));

		DetainedPlayer loaded = repository.loadAll().iterator().next();

		assertEquals(BOB, loaded.getPlayerId());
		assertEquals(2, loaded.getJailId());
		assertEquals(DetainmentState.HANDCUFFED, loaded.getState());
		assertEquals(1_234L, loaded.getTransitExpiresAt());
		assertNull(loaded.getSentenceExpiresAt());
		assertEquals(4, loaded.getWantedAtArrest());
	}
}
