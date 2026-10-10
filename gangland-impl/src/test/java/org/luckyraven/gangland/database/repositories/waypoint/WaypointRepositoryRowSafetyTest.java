package org.luckyraven.gangland.database.repositories.waypoint;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.testkit.BukkitStatics;

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
 * T-199 (impl half, waypoints): numeric waypoint columns may arrive as Long, BigDecimal or NULL. The hard casts threw
 * and failed the whole load; each numeric column now reads through {@code Number}, a NULL reads as zero.
 */
@DisplayName("WaypointRepository row safety - numeric columns read through Number (T-199)")
class WaypointRepositoryRowSafetyTest {

	private BukkitStatics       bukkit;
	private DatabaseBackend     backend;
	private WaypointRepository  repository;

	@BeforeEach
	void setUp() {
		bukkit     = BukkitStatics.install();
		backend    = mock(DatabaseBackend.class);
		repository = new WaypointRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("Long and BigDecimal numeric columns load with their values")
	void widerNumericTypes_load() throws SQLException {
		doReturn(Collections.singletonList(new Object[] {
				4L, 2L, "ward", "world", new BigDecimal("1"), 2.0d, 3L, 0L, 0L, "SPAWN", 1L, 300L, 60L,
				new BigDecimal("12.5"), new BigDecimal("50")
		})).when(backend).query(anyString(), any());

		Collection<Waypoint> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		Waypoint waypoint = loaded.iterator().next();
		assertEquals("ward", waypoint.getName());
		assertEquals(4, repository.getHighestStoredId());
	}

	@Test
	@DisplayName("NULL numeric columns read as zero instead of throwing")
	void nullNumerics_readAsZero() throws SQLException {
		doReturn(Collections.singletonList(new Object[] {
				null, null, "ward", "world", null, null, null, null, null, "SPAWN", null, null, null, null, null
		})).when(backend).query(anyString(), any());

		Collection<Waypoint> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		assertEquals(0, repository.getHighestStoredId());
	}
}
