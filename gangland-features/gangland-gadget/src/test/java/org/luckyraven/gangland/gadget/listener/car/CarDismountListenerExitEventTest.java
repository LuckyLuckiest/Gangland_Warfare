package org.luckyraven.gangland.gadget.listener.car;

import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.vehicle.CarExitEvent;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleRegistry;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleSession;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P-VEH-1: leaving a gadget car publishes {@link CarExitEvent} with the parked flag, after the car is parked. A
 * dismount that is cancelled (not sneaking) or belongs to no session fires nothing.
 */
@DisplayName("CarDismountListener - CarExitEvent")
class CarDismountListenerExitEventTest {

	private BukkitStatics       bukkit;
	private final List<Event>   fired = new ArrayList<>();
	private CarService          carService;
	private VehicleRegistry     registry;
	private CarDismountListener listener;
	private UUID                carUuid;
	private Player              player;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		doAnswer(invocation -> {
			fired.add(invocation.getArgument(0));
			return null;
		}).when(bukkit.pluginManager()).callEvent(any(Event.class));

		carService = mock(CarService.class);
		registry   = mock(VehicleRegistry.class);
		when(carService.getVehicleRegistry()).thenReturn(registry);
		when(carService.getPlugin()).thenReturn(mock(JavaPlugin.class));
		listener = new CarDismountListener(carService);

		carUuid = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private VehicleExitEvent exitFrom(Minecart cart) {
		VehicleExitEvent event = mock(VehicleExitEvent.class);
		when(event.getExited()).thenReturn(player);
		when(event.getVehicle()).thenReturn(cart);
		return event;
	}

	private Minecart cart() {
		Minecart cart = mock(Minecart.class);
		when(cart.getUniqueId()).thenReturn(carUuid);
		when(cart.getLocation()).thenReturn(new Location(null, 1, 64, 1));
		return cart;
	}

	@Test
	@DisplayName("sneakingDismountFiresCarExitParkedAfterParking")
	void sneakingDismountFiresCarExitParkedAfterParking() {
		Minecart cart = cart();
		when(registry.getByEntity(carUuid)).thenReturn(mock(VehicleSession.class));
		when(player.isSneaking()).thenReturn(true);

		listener.onDismount(exitFrom(cart));

		assertEquals(1, fired.size());
		CarExitEvent event = (CarExitEvent) fired.get(0);
		assertSame(player, event.getPlayer());
		assertSame(cart, event.getVehicle());
		assertTrue(event.isParked());
		verify(carService).parkCar(carUuid);
	}

	@Test
	@DisplayName("cancelledDismountFiresNothing: not sneaking, alive, not downed -> physics eject is cancelled")
	void cancelledDismountFiresNothing() {
		Minecart cart = cart();
		when(registry.getByEntity(carUuid)).thenReturn(mock(VehicleSession.class));
		when(player.isSneaking()).thenReturn(false);
		when(player.isDead()).thenReturn(false);

		VehicleExitEvent event = exitFrom(cart);
		listener.onDismount(event);

		assertTrue(fired.isEmpty());
	}

	@Test
	@DisplayName("vehicleWithoutSessionFiresNothing: a plain minecart exit is not a car exit")
	void vehicleWithoutSessionFiresNothing() {
		Minecart cart = cart();
		when(registry.getByEntity(carUuid)).thenReturn(null);
		when(player.isSneaking()).thenReturn(true);

		listener.onDismount(exitFrom(cart));

		assertTrue(fired.isEmpty());
	}

	@Test
	@DisplayName("deathDismountStillFiresOnce: a dead driver is let out and counts as one exit")
	void deathDismountStillFiresOnce() {
		Minecart cart = cart();
		when(registry.getByEntity(carUuid)).thenReturn(mock(VehicleSession.class));
		when(player.isSneaking()).thenReturn(false);
		when(player.isDead()).thenReturn(true);

		listener.onDismount(exitFrom(cart));

		assertEquals(1, fired.size());
	}
}
