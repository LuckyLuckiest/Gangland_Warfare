package org.luckyraven.gangland.gadget.car.vehicle;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.ExhaustSide;
import org.luckyraven.gangland.gadget.car.vehicle.entity.VehicleEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Pins {@link ParkedVehicle}'s fuel/durability clamping (gadgets-cars-fuel-jetpack.md — the in-memory record for a
 * placed-but-undriven car, used throughout W3/W7/W10).
 */
@DisplayName("ParkedVehicle — durability and fuel clamping")
class ParkedVehicleTest {

	private static ParkedVehicle parkedVehicle(int fuel, int maxFuel, int durability) {
		Car car = Car.builder()
		             .carId("test_car")
		             .displayName("Test Car")
		             .itemMaterial(Material.MINECART)
		             .customModelData(0)
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
		return new ParkedVehicle(entity, car, UUID.randomUUID(), fuel, maxFuel, durability, ExhaustSide.LEFT);
	}

	@Test
	@DisplayName("damage subtracts and clamps at zero, never negative")
	void damage_clampsAtZero() {
		ParkedVehicle vehicle = parkedVehicle(1000, 2000, 100);

		vehicle.damage(60);
		assertEquals(40, vehicle.getDurability());

		vehicle.damage(1000); // way more than remaining
		assertEquals(0, vehicle.getDurability());
	}

	@Test
	@DisplayName("addFuel adds and clamps at zero for a negative amount — but NOT at maxFuel (Observation-adjacent: no upper clamp)")
	void addFuel_clampsAtZeroOnly_noUpperBound() {
		ParkedVehicle vehicle = parkedVehicle(500, 1000, 100);

		vehicle.addFuel(300);
		assertEquals(800, vehicle.getFuel());

		// Unlike VehicleSession.addFuel (which clamps at maxFuel), ParkedVehicle.addFuel has no upper clamp at
		// all — only Math.max(0, ...) against going negative. A large enough refuel can push fuel past maxFuel.
		vehicle.addFuel(5000);
		assertEquals(5800, vehicle.getFuel(),
		             "ParkedVehicle.addFuel has no maxFuel ceiling, unlike VehicleSession.addFuel");

		vehicle.addFuel(-999999);
		assertEquals(0, vehicle.getFuel(), "a large negative delta still clamps at zero, never goes negative");
	}

	@Test
	@DisplayName("isDestroyed is true at and below zero durability, false above it")
	void isDestroyed_trueAtOrBelowZero() {
		ParkedVehicle vehicle = parkedVehicle(500, 1000, 1);

		assertFalse(vehicle.isDestroyed());

		vehicle.damage(1);
		assertTrue(vehicle.isDestroyed());

		vehicle.damage(50); // already destroyed, stays destroyed and clamped at 0
		assertTrue(vehicle.isDestroyed());
		assertEquals(0, vehicle.getDurability());
	}

}
