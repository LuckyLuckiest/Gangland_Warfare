package org.luckyraven.gangland.events.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class CarMoveEventTest {

	@Test
	@DisplayName("getters return the constructor values; mutating the passed Locations does not change getFrom/getTo")
	void carriesSpeedAndClonesLocations() {
		Player   driver  = mock(Player.class);
		Entity   vehicle = mock(Entity.class);
		Location from    = new Location(null, 1, 64, 2);
		Location to      = new Location(null, 5, 64, 6);

		CarMoveEvent event = new CarMoveEvent(driver, vehicle, from, to, 12.5);
		from.setX(999);
		to.setX(999);

		assertSame(driver, event.getDriver());
		assertSame(vehicle, event.getVehicle());
		assertEquals(12.5, event.getBlocksPerSecond(), 1.0E-9);
		assertEquals(1, event.getFrom().getX(), 1.0E-9);
		assertEquals(5, event.getTo().getX(), 1.0E-9);
	}

	@Test
	@DisplayName("a listener mutating the returned Location does not change the event")
	void gettersReturnCopies() {
		CarMoveEvent event = new CarMoveEvent(mock(Player.class), mock(Entity.class), new Location(null, 1, 64, 2),
		                                      new Location(null, 5, 64, 6), 1);
		Location handed = event.getFrom();
		handed.setX(777);

		assertNotSame(handed, event.getFrom());
		assertEquals(1, event.getFrom().getX(), 1.0E-9);
	}

	@Test
	@DisplayName("is synchronous and not cancellable")
	void syncAndNotCancellable() {
		CarMoveEvent event = new CarMoveEvent(mock(Player.class), mock(Entity.class), new Location(null, 0, 0, 0),
		                                      new Location(null, 0, 0, 0), 0);
		assertFalse(event.isAsynchronous());
		assertFalse(Cancellable.class.isAssignableFrom(CarMoveEvent.class));
	}
}
