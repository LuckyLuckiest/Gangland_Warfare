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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link VehicleSession}'s durability/fuel clamping (gadgets-cars-fuel-jetpack.md W5 — the per-tick movement
 * loop — and W6/W7/W9's park/force-park teardown, all of which read these clamped values before persisting).
 *
 * <p>{@code Bukkit.createBossBar(...)} is called from the constructor, so this suite installs
 * {@link BukkitStatics} and stubs it explicitly (the base fixture only wires scheduler/plugin-manager/services).
 */
@DisplayName("VehicleSession — durability and fuel clamping")
class VehicleSessionTest {

	private BukkitStatics bukkit;

	@AfterEach
	void tearDown() {
		if (bukkit != null) bukkit.close();
	}

	private VehicleSession session(int initialDurability, int initialFuel, int maxFuel) {
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
		             .fuelEnabled(true)
		             .fuelKey("gasoline")
		             .maxFuel(maxFuel)
		             .maxDurability(500)
		             .build();

		VehicleEntity entity = mock(VehicleEntity.class);
		Player driver = mock(Player.class);
		when(driver.getUniqueId()).thenReturn(UUID.randomUUID());

		return new VehicleSession(entity, car, driver, initialDurability, initialFuel, maxFuel, ExhaustSide.LEFT);
	}

	@Test
	@DisplayName("damage subtracts and clamps at zero")
	void damage_clampsAtZero() {
		VehicleSession session = session(100, 500, 1000);

		session.damage(40);
		assertEquals(60, session.getCurrentDurability());

		session.damage(1000);
		assertEquals(0, session.getCurrentDurability());
	}

	@Test
	@DisplayName("consumeFuel subtracts and clamps at zero; hasFuel reflects the clamp")
	void consumeFuel_clampsAtZero_hasFuelReflectsIt() {
		VehicleSession session = session(500, 10, 1000);

		assertTrue(session.hasFuel());

		session.consumeFuel(10);
		assertEquals(0, session.getCurrentFuel());
		assertFalse(session.hasFuel());

		session.consumeFuel(50); // already empty, stays clamped
		assertEquals(0, session.getCurrentFuel());
	}

	@Test
	@DisplayName("addFuel adds and clamps at maxFuel — unlike ParkedVehicle.addFuel, which has no upper clamp")
	void addFuel_clampsAtMaxFuel() {
		VehicleSession session = session(500, 900, 1000);

		session.addFuel(50);
		assertEquals(950, session.getCurrentFuel());

		session.addFuel(500); // would overshoot 1000
		assertEquals(1000, session.getCurrentFuel(), "VehicleSession.addFuel clamps at maxFuel");
	}

	@Test
	@DisplayName("isDestroyed is true at and below zero durability")
	void isDestroyed_trueAtOrBelowZero() {
		VehicleSession session = session(1, 500, 1000);

		assertFalse(session.isDestroyed());

		session.damage(1);
		assertTrue(session.isDestroyed());
	}

	@Test
	@DisplayName("a null exhaustSide argument is replaced with a random concrete side, never left null")
	void nullExhaustSide_replacedWithRandom() {
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
		             .maxHealth(100.0)
		             .fuelEnabled(false)
		             .maxDurability(500)
		             .build();
		VehicleEntity entity = mock(VehicleEntity.class);
		Player driver = mock(Player.class);
		when(driver.getUniqueId()).thenReturn(UUID.randomUUID());

		VehicleSession session = new VehicleSession(entity, car, driver, 500, 0, 0, null);

		assertTrue(session.getExhaustSide() != null);
	}

}
