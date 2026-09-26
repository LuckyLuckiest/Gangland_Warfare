package org.luckyraven.gangland.lootchest.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * A loot chest's cooldown must survive a restart: before the fix {@code cooldownEndTime} had no column, so every
 * reload came back with no cooldown and {@code tryOpenChest} regenerated the loot and re-paid the reward.
 */
@DisplayName("LootChestRepository — cooldown survives a reload")
class LootChestRepositoryTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: SQLite holds the .db handle past the test
	Path tempDir;

	private SqliteBackend        backend;
	private LootChestRepository  repository;
	private MockedStatic<Bukkit> bukkit;

	@BeforeEach
	void setUp() throws SQLException {
		bukkit  = mockStatic(Bukkit.class); // Bukkit.getWorld -> null
		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("loot.db")));
		backend.applySchema(TableSchemas.fromTable(new LootChestTable()));

		repository = new LootChestRepository(PluginMocks.plugin(tempDir), mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	@Test
	@DisplayName("a chest on cooldown is still on cooldown after loadAll")
	void cooldownEndTime_roundTrips() {
		long endTime = System.currentTimeMillis() + 300_000;
		LootChestData chest = LootChestData.builder()
		                                   .id(UUID.randomUUID())
		                                   .location(new Location(null, 1, 2, 3))
		                                   .lootTableId("common")
		                                   .respawnTime(300)
		                                   .inventorySize(27)
		                                   .displayName("Chest")
		                                   .build();
		chest.startCooldown(300);
		chest.setCooldownEndTime(endTime);

		repository.save(chest);
		LootChestData loaded = repository.loadAll().iterator().next();

		assertEquals(endTime, loaded.getCooldownEndTime());
		assertTrue(loaded.isOnCooldown(), "a restart inside the cooldown window must not reopen the chest");
		assertTrue(loaded.isBlocked(), "empty + on cooldown blocks tryOpenChest");
	}
}
