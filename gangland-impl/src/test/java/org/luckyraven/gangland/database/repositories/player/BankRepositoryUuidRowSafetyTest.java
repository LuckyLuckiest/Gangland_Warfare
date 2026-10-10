package org.luckyraven.gangland.database.repositories.player;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.testkit.BukkitStatics;

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
 * T-262 (bank table): one row with a NULL or malformed uuid is skipped with a warning; the rest of the table loads.
 */
@DisplayName("BankRepository - a bad uuid row is skipped, not fatal (T-262)")
class BankRepositoryUuidRowSafetyTest {

	private static final String ID = "6f1f3b2e-0000-4000-8000-000000000003";

	private BukkitStatics   bukkit;
	private DatabaseBackend backend;
	private BankRepository  repository;

	@BeforeEach
	void setUp() {
		bukkit     = BukkitStatics.install();
		backend    = mock(DatabaseBackend.class);
		repository = new BankRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a NULL uuid row is skipped and the valid row still loads")
	void nullUuidRow_isSkipped_validRowLoads() throws SQLException {
		Object[] bad   = {null, "name", new BigDecimal("5"), null, 0.0d, null, null, null, null};
		Object[] valid = {ID, "name", new BigDecimal("5"), null, 0.0d, null, null, null, null};
		doReturn(Arrays.asList(bad, valid)).when(backend).query(anyString(), any());

		Collection<Bank> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		assertEquals(UUID.fromString(ID), loaded.iterator().next().getUuid());
	}
}
