package org.luckyraven.gangland.database.repositories.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.database.tables.gang.GangAllianceTable;
import org.luckyraven.gangland.database.tables.gang.GangTable;
import org.luckyraven.gangland.file.configuration.gang.GanglandGangSettings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchema;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Round-trips {@code gang_ally} through the DatabaseBackend SPI to pin the {@code GangAllianceTable} key shape.
 *
 * <p>Regression net for <b>GR-05</b> (Observation #5, gangs-ranks-mail.md): the table used to declare only
 * {@code gang_id} as its primary key, with {@code ally_id} merely {@code UNIQUE}. {@code TableBackend.upsert}/
 * {@code upsertAll} derive their conflict target from the primary-key columns, so every alliance a gang owned
 * collapsed onto a single row — a gang with two allies silently lost one on each save — while the
 * {@code UNIQUE(ally_id)} constraint additionally forbade a gang from being the allied side of more than one
 * alliance. The key is now the composite {@code (gang_id, ally_id)}.
 *
 * <p>Also covers <b>GR-06</b>'s persistence half: {@code delete(GangAlliance)} must physically remove the row,
 * since the autosave path is upsert-only and can never remove one.
 *
 * <p>Uses a disabled {@code PluginMocks} plugin so every repository write takes the synchronous inline path.
 */
@DisplayName("GangAllianceRepository — composite (gang_id, ally_id) round trip")
class GangAllianceRepositorySpiTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari holds the .db handle past the test
	Path tempDir;

	private SqliteBackend          backend;
	private GangAllianceRepository repository;
	private FakeGangLookup         gangLookup;

	private Gang alpha, bravo, charlie;

	@BeforeEach
	void setUp() throws SQLException {
		SettingsFixture.initializeMinimal(tempDir);
		GangSettings.bind(new GanglandGangSettings());

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("alliances.db")));
		backend.applySchema(TableSchemas.fromTable(new GangTable()));
		backend.applySchema(TableSchemas.fromTable(new GangAllianceTable(new GangTable())));

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		repository = new GangAllianceRepository(plugin, mock(DatabaseHandler.class), backend);

		alpha   = new Gang(1);
		bravo   = new Gang(2);
		charlie = new Gang(3);

		gangLookup = new FakeGangLookup(alpha, bravo, charlie);
		repository.setGangLookup(gangLookup);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("GR-05: a gang with two allies keeps both rows across a saveAll")
	void saveAll_gangWithTwoAllies_keepsBothRows() {
		repository.saveAll(List.of(new GangAlliance(alpha, bravo, 100L),
		                           new GangAlliance(alpha, charlie, 200L)));

		Collection<GangAlliance> loaded = repository.loadAll();

		assertEquals(2, loaded.size(), "keying on gang_id alone collapsed both alliances onto one row");
		assertTrue(loaded.stream().anyMatch(alliance -> alliance.ally().getId() == bravo.getId()));
		assertTrue(loaded.stream().anyMatch(alliance -> alliance.ally().getId() == charlie.getId()));
	}

	@Test
	@DisplayName("GR-05: the same gang may be the allied side of several alliances (ally_id is not UNIQUE)")
	void saveAll_sameAllyOnBothSides_keepsEveryRow() {
		repository.saveAll(List.of(new GangAlliance(alpha, charlie, 100L),
		                           new GangAlliance(bravo, charlie, 200L)));

		Collection<GangAlliance> loaded = repository.loadAll();

		assertEquals(2, loaded.size(), "UNIQUE(ally_id) let only one gang list charlie as an ally");
	}

	@Test
	@DisplayName("GR-05: both direction rows of one mutual alliance survive together")
	void saveAll_bothDirections_roundTrip() {
		repository.saveAll(List.of(new GangAlliance(alpha, bravo, 100L),
		                           new GangAlliance(bravo, alpha, 100L)));

		Collection<GangAlliance> loaded = repository.loadAll();

		assertEquals(2, loaded.size());
		assertTrue(loaded.stream().anyMatch(a -> a.gang().getId() == 1 && a.ally().getId() == 2));
		assertTrue(loaded.stream().anyMatch(a -> a.gang().getId() == 2 && a.ally().getId() == 1));
	}

	@Test
	@DisplayName("re-saving the same (gang_id, ally_id) pair upserts instead of duplicating")
	void save_samePair_upserts() {
		repository.save(new GangAlliance(alpha, bravo, 100L));
		repository.save(new GangAlliance(alpha, bravo, 999L));

		Collection<GangAlliance> loaded = repository.loadAll();

		assertEquals(1, loaded.size());
		assertEquals(999L, loaded.iterator().next().since());
	}

	@Test
	@DisplayName("GR-06: delete removes only the named direction row")
	void delete_removesOnlyThatDirection() {
		repository.saveAll(List.of(new GangAlliance(alpha, bravo, 100L),
		                           new GangAlliance(bravo, alpha, 100L),
		                           new GangAlliance(alpha, charlie, 100L)));

		repository.delete(new GangAlliance(alpha, bravo, 100L));

		Collection<GangAlliance> loaded = repository.loadAll();

		assertEquals(2, loaded.size());
		assertFalse(loaded.stream().anyMatch(a -> a.gang().getId() == 1 && a.ally().getId() == 2),
		            "the alpha → bravo row must be gone");
		assertTrue(loaded.stream().anyMatch(a -> a.gang().getId() == 2 && a.ally().getId() == 1),
		           "the bravo → alpha row is a separate row and must survive");
	}

	@Test
	@DisplayName("GR-05: the declared schema keys on (gang_id, ally_id), which is also the upsert conflict target")
	void declaredSchema_primaryKeyIsComposite() {
		TableSchema schema = TableSchemas.fromTable(new GangAllianceTable(new GangTable()));

		assertEquals(List.of("gang_id", "ally_id"), schema.primaryKey(),
		             "TableBackend derives the upsert conflict target from the primary-key columns");
	}

	/** Minimal {@link GangLookupContract} over a fixed set of gangs — the seam {@code doLoadAll} resolves rows through. */
	private static final class FakeGangLookup implements GangLookupContract {

		private final Map<Integer, Gang> gangs = new HashMap<>();

		private FakeGangLookup(Gang... gangs) {
			for (Gang gang : gangs) {
				this.gangs.put(gang.getId(), gang);
			}
		}

		@Override
		public @Nullable Gang findById(int gangId) {
			return gangs.get(gangId);
		}

		@Override
		public Collection<Gang> getAll() {
			return gangs.values();
		}

	}

}
