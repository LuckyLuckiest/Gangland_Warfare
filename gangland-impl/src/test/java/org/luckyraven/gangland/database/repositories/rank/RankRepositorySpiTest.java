package org.luckyraven.gangland.database.repositories.rank;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.database.tables.rank.RankTable;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Proves the DatabaseBackend-SPI path Gangland's repositories migrated onto (Keystone 1.7.1): schema application
 * through the backend diff engine, {@code save}/{@code loadAll}/{@code delete} through {@code TableBackend}, and
 * the bespoke {@code insertInitialRanks} query rewritten onto {@code BackendQueryBuilder}/{@code execute}.
 *
 * <p>Uses a disabled {@code PluginMocks} plugin so every repository write takes the synchronous inline path.
 */
@DisplayName("RankRepository — backend SPI round trip")
class RankRepositorySpiTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari holds the .db handle past the test
	Path tempDir;

	private SqliteBackend  backend;
	private RankRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("ranks.db")));
		backend.applySchema(TableSchemas.fromTable(new RankTable()));

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		repository = new RankRepository(plugin, mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("saved ranks round-trip through loadAll with their vault groups")
	void saveThenLoadAll_roundTrips() {
		Rank boss = new Rank("Boss", 5);
		boss.setVaultGroup("bosses");
		repository.save(boss);
		repository.save(new Rank("Recruit", 6));

		Collection<Rank> loaded = repository.loadAll();

		assertEquals(2, loaded.size());
		Rank loadedBoss = loaded.stream().filter(rank -> rank.getUsedId() == 5).findFirst().orElseThrow();
		assertEquals("Boss", loadedBoss.getName());
		assertEquals("bosses", loadedBoss.getVaultGroup());
		Rank loadedRecruit = loaded.stream().filter(rank -> rank.getUsedId() == 6).findFirst().orElseThrow();
		assertNull(loadedRecruit.getVaultGroup(), "an unlinked rank must load with a null vault group");
	}

	@Test
	@DisplayName("save on an existing id upserts instead of duplicating")
	void save_upsertsOnPrimaryKey() {
		repository.save(new Rank("Old", 9));
		repository.save(new Rank("Renamed", 9));

		Collection<Rank> loaded = repository.loadAll();

		assertEquals(1, loaded.size());
		assertEquals("Renamed", loaded.iterator().next().getName());
	}

	@Test
	@DisplayName("delete removes exactly the given rank")
	void delete_removesRow() {
		repository.save(new Rank("Keep", 1));
		repository.save(new Rank("Drop", 2));

		repository.delete(new Rank("Drop", 2));

		Collection<Rank> loaded = repository.loadAll();
		assertEquals(1, loaded.size());
		assertEquals("Keep", loaded.iterator().next().getName());
	}

	@Test
	@DisplayName("insertInitialRanks creates tail then head once, and is idempotent")
	void insertInitialRanks_isIdempotent() throws SQLException {
		int[] first = repository.insertInitialRanks("Leader", "Recruit");

		assertEquals(1, first[1], "tail rank is inserted first and takes id 1");
		assertEquals(2, first[0], "head rank follows and takes id 2");

		int[] second = repository.insertInitialRanks("Leader", "Recruit");
		assertArrayEquals(first, second, "re-running on startup must resolve, not re-insert");

		List<Object[]> names = backend.query("SELECT name FROM rank_tree", rs -> new Object[]{rs.getString("name")});
		assertEquals(2, names.size());
	}

}
