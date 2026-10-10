package org.luckyraven.gangland.gang.database.repositories.rank;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.gang.database.tables.rank.RankTable;
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
 * the bespoke rank-id query ({@code nextRankId}) rewritten onto {@code BackendQueryBuilder}/{@code execute}.
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
	@DisplayName("nextRankId on an empty table is 1")
	void nextRankId_emptyTable_isOne() throws SQLException {
		assertEquals(1, repository.nextRankId());
	}

	@Test
	@DisplayName("GR-21: after a delete leaves a gap, the next id is max(id)+1, never total+1 (which collides)")
	void newRankId_afterDeleteWithGap_isMaxPlusOne() throws SQLException {
		repository.save(new Rank("one", 1));
		repository.save(new Rank("two", 2));
		repository.save(new Rank("three", 3));
		repository.delete(new Rank("two", 2));

		assertEquals(4, repository.nextRankId(), "max(id)+1 = 4; total+1 would be 3 and collide with rank three");
	}

	@Test
	@DisplayName("unlinked ranks persist on a legacy vault_group NOT NULL column")
	void unlinkedRanks_persistOnLegacyNotNullColumn() throws SQLException {
		// Databases created before vault_group became nullable keep the NOT NULL constraint: applySchema only
		// diffs column names, so the legacy DDL survives every restart.
		backend.execute("DROP TABLE rank_tree");
		backend.execute("CREATE TABLE rank_tree (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, " +
		                "vault_group TEXT NOT NULL DEFAULT '')");
		backend.applySchema(TableSchemas.fromTable(new RankTable()));

		repository.save(new Rank("owner", 1));
		repository.save(new Rank("member", 2));
		repository.save(new Rank("officer", 3));

		Collection<Rank> loaded = repository.loadAll();
		assertEquals(3, loaded.size());
		assertTrue(loaded.stream().allMatch(rank -> rank.getVaultGroup() == null),
		           "an unlinked rank must still load with a null vault group");
	}

}
