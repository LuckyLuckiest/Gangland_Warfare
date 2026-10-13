package org.luckyraven.gangland.events.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CarTheftAttemptEventTest {

	@Test
	@DisplayName("owner may be null; cancel flag round-trips")
	void ownerNullableAndCancellable() {
		Player thief   = mock(Player.class);
		Entity vehicle = mock(Entity.class);
		CarTheftAttemptEvent event = new CarTheftAttemptEvent(thief, vehicle, null);

		assertNull(event.getOwner());
		assertSame(thief, event.getThief());
		assertSame(vehicle, event.getVehicle());
		assertFalse(event.isCancelled());
		event.setCancelled(true);
		assertTrue(event.isCancelled());
		assertFalse(event.isAsynchronous());
	}

	@Test
	@DisplayName("a present owner id is returned as given")
	void ownerReturned() {
		UUID owner = UUID.randomUUID();
		assertEquals(owner, new CarTheftAttemptEvent(mock(Player.class), mock(Entity.class), owner).getOwner());
	}
}
