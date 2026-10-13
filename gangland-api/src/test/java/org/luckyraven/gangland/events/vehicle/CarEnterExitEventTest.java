package org.luckyraven.gangland.events.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CarEnterExitEventTest {

	@Test
	@DisplayName("CarEnterEvent carries player, vehicle and car type; sync; not cancellable")
	void enterCarriesFields() {
		Player player  = mock(Player.class);
		Entity vehicle = mock(Entity.class);
		CarEnterEvent event = new CarEnterEvent(player, vehicle, "sedan");

		assertSame(player, event.getPlayer());
		assertSame(vehicle, event.getVehicle());
		assertEquals("sedan", event.getCarType());
		assertFalse(event.isAsynchronous());
		assertFalse(Cancellable.class.isAssignableFrom(CarEnterEvent.class));
	}

	@Test
	@DisplayName("CarExitEvent carries player, vehicle and the parked flag both ways; sync; not cancellable")
	void exitCarriesFields() {
		Player player  = mock(Player.class);
		Entity vehicle = mock(Entity.class);
		CarExitEvent parked  = new CarExitEvent(player, vehicle, true);
		CarExitEvent ejected = new CarExitEvent(player, vehicle, false);

		assertSame(player, parked.getPlayer());
		assertSame(vehicle, parked.getVehicle());
		assertTrue(parked.isParked());
		assertFalse(ejected.isParked());
		assertFalse(parked.isAsynchronous());
		assertFalse(Cancellable.class.isAssignableFrom(CarExitEvent.class));
	}
}
