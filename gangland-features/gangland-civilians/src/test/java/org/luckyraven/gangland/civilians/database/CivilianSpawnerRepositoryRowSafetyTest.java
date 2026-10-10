package org.luckyraven.gangland.civilians.database;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawner;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T-199 (civilians half): numeric spawner columns may arrive as Long, BigDecimal, Float or NULL. The hard casts threw
 * and failed the whole load; each numeric column now reads through {@code Number}, a NULL reads as zero.
 */
@DisplayName("CivilianSpawnerRepository row safety - numeric columns read through Number (T-199)")
class CivilianSpawnerRepositoryRowSafetyTest {

	private BukkitStatics             bukkit;
	private DatabaseBackend           backend;
	private CivilianSpawnerRepository repository;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		World world = mock(World.class);
		when(world.getName()).thenReturn("world");
		bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

		backend    = mock(DatabaseBackend.class);
		repository = new CivilianSpawnerRepository(mock(org.bukkit.plugin.java.JavaPlugin.class),
		                                           mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private void rows(Object[]... rows) throws SQLException {
		doReturn(Arrays.asList(rows)).when(backend).query(anyString(), any());
	}

	@Test
	@DisplayName("Long, BigDecimal and Float numeric columns load with their values")
	void widerNumericTypes_load() throws SQLException {
		rows(new Object[] {
				7L, null, null, "world", new BigDecimal("1.5"), 2.0d, 3L, 90F, 0L
		});

		Collection<CivilianSpawner> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		CivilianSpawner spawner = loaded.iterator().next();
		assertEquals(7, spawner.getId());
		assertEquals(1.5, spawner.getLocation().getX());
		assertEquals(3.0, spawner.getLocation().getZ());
	}

	@Test
	@DisplayName("NULL numeric columns read as zero instead of throwing")
	void nullNumerics_readAsZero() throws SQLException {
		rows(new Object[] {
				null, null, null, "world", null, null, null, null, null
		});

		Collection<CivilianSpawner> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		assertEquals(0, loaded.iterator().next().getId());
	}
}
