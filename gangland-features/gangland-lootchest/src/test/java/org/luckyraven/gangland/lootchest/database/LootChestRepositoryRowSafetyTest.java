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

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * T-199 (lootchest half): a numeric column can come back as Long, BigDecimal, Integer or NULL on MySQL. The hard casts
 * threw ClassCastException / NullPointerException and failed the whole load; every numeric column now reads through
 * {@code Number} and a NULL reads as zero.
 */
@DisplayName("LootChestRepository row safety - numeric columns read through Number (T-199)")
class LootChestRepositoryRowSafetyTest {

	private static final String ID = "6f1f3b2e-0000-4000-8000-000000000001";

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

	private void rows(Object[]... rows) throws SQLException {
		doReturn(Arrays.asList(rows)).when(backend).query(anyString(), any());
	}

	@Test
	@DisplayName("Long, BigDecimal and Integer numeric columns load with their values")
	void widerNumericTypes_load() throws SQLException {
		rows(new Object[] {
				ID, "world", new BigDecimal("1.5"), 2L, 3.0d, "common", null, 300L, new BigDecimal("27"), "Chest", 0L,
				1L, 0L
		});

		Collection<LootChestData> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		LootChestData chest = loaded.iterator().next();
		assertEquals(1.5, chest.getLocation().getX());
		assertEquals(2.0, chest.getLocation().getY());
		assertEquals(27, chest.getInventorySize());
		assertTrue(chest.isLooted());
	}

	@Test
	@DisplayName("NULL numeric columns read as zero instead of throwing")
	void nullNumerics_readAsZero() throws SQLException {
		rows(new Object[] {
				ID, "world", null, null, null, "common", null, null, null, "Chest", null, null, null
		});

		Collection<LootChestData> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		LootChestData chest = loaded.iterator().next();
		assertEquals(0, chest.getInventorySize());
		assertFalse(chest.isLooted());
	}

	@Test
	@DisplayName("T-262: a row with a NULL uuid is skipped and the valid row still loads")
	void nullUuidRow_isSkipped_validRowLoads() throws SQLException {
		Object[] bad   = {null, "world", null, null, null, "common", null, null, null, "Chest", null, null, null};
		Object[] valid = {ID, "world", null, null, null, "common", null, null, null, "Chest", null, null, null};
		rows(bad, valid);

		Collection<LootChestData> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
	}
}
