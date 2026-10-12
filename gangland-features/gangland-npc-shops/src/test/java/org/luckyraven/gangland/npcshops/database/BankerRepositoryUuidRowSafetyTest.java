package org.luckyraven.gangland.npcshops.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.gangland.npcshops.banker.BankerData;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * T-262: one row with a NULL or malformed uuid is skipped with a warning; the rest of the banker table still loads.
 */
@DisplayName("BankerRepositoryUuidRowSafetyTest - a bad uuid row is skipped, not fatal (T-262)")
class BankerRepositoryUuidRowSafetyTest {

	private static final String ID = "6f1f3b2e-0000-4000-8000-000000000001";

	private BukkitStatics   bukkit;
	private DatabaseBackend backend;
	private BankerRepository          repository;

	@BeforeEach
	void setUp() {
		bukkit     = BukkitStatics.install();
		backend    = mock(DatabaseBackend.class);
		repository = new BankerRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private void rows(Object[]... rows) throws SQLException {
		doReturn(Arrays.asList(rows)).when(backend).query(anyString(), any());
	}

	@Test
	@DisplayName("a NULL uuid row is skipped and the valid row still loads")
	void nullUuidRow_isSkipped_validRowLoads() throws SQLException {
		Object[] valid = {ID, "world", 1.0d, 2.0d, 3.0d, 0.0d, 0.0d, null};
		Object[] bad   = {null, "world", 1.0d, 2.0d, 3.0d, 0.0d, 0.0d, null};
		rows(bad, valid);

		Collection<BankerData> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		assertEquals(UUID.fromString(ID), ((BankerData) loaded.iterator().next()).getId());
	}
}
