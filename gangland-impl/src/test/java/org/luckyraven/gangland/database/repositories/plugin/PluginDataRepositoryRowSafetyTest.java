package org.luckyraven.gangland.database.repositories.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.plugin.PluginData;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * T-199 (impl half, plugin data): the timestamp columns may arrive as Long, BigDecimal or NULL. The hard casts threw
 * and failed the whole load; each numeric column now reads through {@code Number}, a NULL reads as zero.
 */
@DisplayName("PluginDataRepository row safety - numeric columns read through Number (T-199)")
class PluginDataRepositoryRowSafetyTest {

	private DatabaseBackend        backend;
	private PluginDataRepository   repository;

	@Test
	@DisplayName("Long and BigDecimal timestamp columns load with their values")
	void widerNumericTypes_load() throws SQLException {
		backend    = mock(DatabaseBackend.class);
		repository = new PluginDataRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend);
		doReturn(Collections.singletonList(new Object[] { 1L, new BigDecimal("1000"), 2000L, 3000L })).when(backend)
		      .query(anyString(), any());

		Collection<PluginData> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		PluginData data = loaded.iterator().next();
		assertEquals(1, data.getId());
		assertEquals(1000L, data.getPluginActivateDate());
		assertEquals(3000L, data.getScheduledScanDate());
	}

	@Test
	@DisplayName("NULL timestamp columns read as zero instead of throwing")
	void nullNumerics_readAsZero() throws SQLException {
		backend    = mock(DatabaseBackend.class);
		repository = new PluginDataRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend);
		doReturn(Collections.singletonList(new Object[] { null, null, null, null })).when(backend).query(anyString(), any());

		Collection<PluginData> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		assertEquals(0L, loaded.iterator().next().getPluginActivateDate());
	}
}
