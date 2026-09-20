package org.luckyraven.gangland.gadget.listener.car;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.ExhaustSide;
import org.luckyraven.gangland.gadget.car.access.CarAccessPolicy;
import org.luckyraven.gangland.gadget.car.message.CarMessageContract;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleRegistry;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleSession;
import org.luckyraven.gangland.gadget.car.vehicle.entity.VehicleEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B7 guard (WS7 G5): with {@code Settings.isBartizanAvailable()} stubbed {@code false}, drives the real
 * {@code CarDamageListener.onVehicleDamage} against an active {@link VehicleSession} and asserts the applied
 * damage equals exactly the vanilla fallback ({@code Math.max(1, (int) Math.ceil(event.getDamage()))}) — never a
 * Bartizan-derived melee value. {@link CarMeleeWeaponLookup} is a static helper with no instance to verify a call
 * against, so the observable here is the damage VALUE actually applied to the session, not "non-invocation" of a
 * mock (an earlier review round rejected a {@code verify(mock, never())}-only shortcut for exactly this reason).
 */
@DisplayName("CarDamageListener.onVehicleDamage — Bartizan-unavailable guard (WS7 G5, B7)")
class CarMeleeWeaponLookupGuardTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	@Test
	@DisplayName("Bartizan unavailable: applied damage is the vanilla punch fallback, never a melee weapon value")
	void bartizanUnavailable_appliesVanillaFallbackDamage() {
		stubBartizanUnavailable();

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
		Player        driver = mock(Player.class);
		when(driver.getUniqueId()).thenReturn(UUID.randomUUID());

		int initialDurability = 100;
		VehicleSession session = new VehicleSession(entity, car, driver, initialDurability, 0, 0, ExhaustSide.LEFT);

		UUID            entityUUID      = UUID.randomUUID();
		VehicleRegistry vehicleRegistry = mock(VehicleRegistry.class);
		when(vehicleRegistry.getByEntity(entityUUID)).thenReturn(session);

		CarService carService = mock(CarService.class);
		when(carService.getVehicleRegistry()).thenReturn(vehicleRegistry);
		when(carService.getParkedVehicle(entityUUID)).thenReturn(null);
		when(carService.getPlugin()).thenReturn(mock(JavaPlugin.class));

		CarAccessPolicy    accessPolicy   = new CarAccessPolicy(null);
		CarMessageContract messages       = mock(CarMessageContract.class);
		CarDamageState     carDamageState = new CarDamageState();

		CarDamageListener listener = new CarDamageListener(carService, accessPolicy, messages, carDamageState);

		Minecart minecart = mock(Minecart.class);
		when(minecart.getUniqueId()).thenReturn(entityUUID);
		Player attacker = mock(Player.class);
		when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());

		double rawDamage = 3.2;
		VehicleDamageEvent event = new VehicleDamageEvent(minecart, attacker, rawDamage);

		listener.onVehicleDamage(event);

		int expectedFallback = Math.max(1, (int) Math.ceil(rawDamage));
		assertEquals(initialDurability - expectedFallback, session.getCurrentDurability(),
		            "with Bartizan unavailable, CarMeleeWeaponLookup must never be consulted - only the vanilla "
		            + "punch fallback may be applied");
	}

	private static void stubBartizanUnavailable() {
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.isPluginEnabled("Bartizan")).thenReturn(false);
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);

		when(Bukkit.getServer().createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
				.thenReturn(mock(BossBar.class));
	}
}
