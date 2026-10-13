package org.luckyraven.gangland.events.vehicle;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CarDamageEventTest {

	@Test
	@DisplayName("cancel flag round-trips (default false) and the damager may be null")
	void isCancellable() {
		Entity vehicle = mock(Entity.class);
		CarDamageEvent event = new CarDamageEvent(vehicle, null, 3.5);

		assertFalse(event.isCancelled());
		event.setCancelled(true);
		assertTrue(event.isCancelled());
		event.setCancelled(false);
		assertFalse(event.isCancelled());
		assertNull(event.getDamager());
		assertSame(vehicle, event.getVehicle());
		assertEquals(3.5, event.getDamage(), 1.0E-9);
		assertFalse(event.isAsynchronous());
	}

	@Test
	@DisplayName("a present damager is returned as given")
	void damagerReturned() {
		Entity damager = mock(Entity.class);
		assertSame(damager, new CarDamageEvent(mock(Entity.class), damager, 1).getDamager());
	}
}
