package org.luckyraven.gangland.gadget.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.car.ParkedCar;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * T-199 (gadget half): numeric parked-car columns may arrive as Long, BigDecimal or NULL. The hard casts threw and
 * failed the whole load; each numeric column now reads through {@code Number}, a NULL reads as zero.
 */
@DisplayName("ParkedCarRepository row safety - numeric columns read through Number (T-199)")
class ParkedCarRepositoryRowSafetyTest {

	private DatabaseBackend     backend;
	private ParkedCarRepository repository;

	@BeforeEach
	void setUp() {
		backend    = mock(DatabaseBackend.class);
		repository = new ParkedCarRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend);
	}

	private void rows(Object[]... rows) throws SQLException {
		doReturn(Arrays.asList(rows)).when(backend).query(anyString(), any());
	}

	@Test
	@DisplayName("Long, BigDecimal and Integer numeric columns load with their values")
	void widerNumericTypes_load() throws SQLException {
		rows(new Object[] {
				"car-db-1", "sedan", "world", new BigDecimal("1.5"), 2.0d, 3L, new BigDecimal("90"), 50L, 100,
				10L, null, null
		});

		Collection<ParkedCar> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		ParkedCar car = loaded.iterator().next();
		assertEquals(50, car.getFuel());
		assertEquals(100, car.getMaxFuel());
		assertEquals(10, car.getDurability());
	}

	@Test
	@DisplayName("NULL numeric columns read as zero instead of throwing")
	void nullNumerics_readAsZero() throws SQLException {
		rows(new Object[] {
				"car-db-2", "sedan", "world", null, null, null, null, null, null, null, null, null
		});

		Collection<ParkedCar> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		ParkedCar car = loaded.iterator().next();
		assertEquals(0, car.getFuel());
		assertEquals(0, car.getDurability());
	}
}
