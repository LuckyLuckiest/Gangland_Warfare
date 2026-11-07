package org.luckyraven.gangland.lootchest.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.sql.SQLException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * T-918: a chest whose world is not loaded when the rows are read (Bukkit.getWorld is null) must be written back to the
 * world it came from, not to "world".
 */
@DisplayName("LootChestRepository - a chest in a world that is not loaded keeps its world name (T-918)")
class LootChestRepositoryUnloadedWorldTest {

	private BukkitStatics       bukkit;
	private DatabaseBackend     backend;
	private LootChestRepository repository;

	@BeforeEach
	void setUp() {
		bukkit     = BukkitStatics.install();
		backend    = mock(DatabaseBackend.class);
		repository = new LootChestRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("T-918: the world column survives a load and a save while the world is not loaded")
	void loadedWithoutItsWorld_isSavedBackUnderTheSameWorldName() throws SQLException {
		doReturn(Arrays.asList(new Object[][]{
				{"6f1f3b2e-0000-4000-8000-000000000001", "arena_world", 1.5, 2.0, 3.0, "common", null, 300L, 27, "Chest",
				 0L, 0L, 0L}
		})).when(backend).query(anyString(), any());

		LootChestData chest = repository.doLoadAll().iterator().next();

		assertEquals("arena_world", repository.getTable().getData(chest)[1],
		             "the autosave must not move the chest to the main world just because its world is not loaded");
	}
}
