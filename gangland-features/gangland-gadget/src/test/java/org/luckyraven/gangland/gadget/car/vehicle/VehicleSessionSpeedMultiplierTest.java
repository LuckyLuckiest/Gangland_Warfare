package org.luckyraven.gangland.gadget.car.vehicle;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.ExhaustSide;
import org.luckyraven.gangland.gadget.car.vehicle.entity.VehicleEntity;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P-VEH-2: a session carries a speed multiplier (default 1.0, clamped 0..2) that the movement task applies.
 */
@DisplayName("VehicleSession - speed multiplier")
class VehicleSessionSpeedMultiplierTest {

	private BukkitStatics bukkit;

	@AfterEach
	void tearDown() {
		if (bukkit != null) bukkit.close();
	}

	private VehicleSession session() {
		bukkit = BukkitStatics.install();
		bukkit.statics()
		      .when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
		      .thenReturn(mock(BossBar.class));

		Car car = Car.builder()
		             .carId("test_car")
		             .displayName("Test Car")
		             .itemMaterial(Material.MINECART)
		             .lore(List.of())
		             .maxSpeed(0.8)
		             .acceleration(0.04)
		             .deceleration(0.02)
		             .turnSpeed(4.0)
		             .maxHealth(100.0)
		             .fuelEnabled(false)
		             .maxDurability(500)
		             .build();

		Player driver = mock(Player.class);
		when(driver.getUniqueId()).thenReturn(UUID.randomUUID());

		return new VehicleSession(mock(VehicleEntity.class), car, driver, driver.getUniqueId(), 500, 0, 0,
		                          ExhaustSide.LEFT);
	}

	@Test
	@DisplayName("speedMultiplierDefaultsToOne")
	void speedMultiplierDefaultsToOne() {
		assertEquals(1.0, session().getSpeedMultiplier(), 1e-9);
	}

	@Test
	@DisplayName("speedMultiplierClamps: above 2 -> 2, below 0 -> 0, in range kept")
	void speedMultiplierClamps() {
		VehicleSession session = session();

		session.setSpeedMultiplier(5.0);
		assertEquals(2.0, session.getSpeedMultiplier(), 1e-9);

		session.setSpeedMultiplier(-1.0);
		assertEquals(0.0, session.getSpeedMultiplier(), 1e-9);

		session.setSpeedMultiplier(0.35);
		assertEquals(0.35, session.getSpeedMultiplier(), 1e-9);
	}

	@Test
	@DisplayName("speedMultiplierRejectsNaN: NaN falls back to 1.0 instead of freezing or flinging the car")
	void speedMultiplierRejectsNaN() {
		VehicleSession session = session();

		session.setSpeedMultiplier(0.5);
		session.setSpeedMultiplier(Double.NaN);

		assertEquals(1.0, session.getSpeedMultiplier(), 1e-9);
	}
}
