package org.luckyraven.gangland.events.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class VehicleEventsHandlerListTest {

	@Test
	@DisplayName("each vehicle event returns its own static HandlerList from getHandlers(), and the five lists are distinct")
	void eachEventHasStaticHandlerList() {
		Player   p = mock(Player.class);
		Entity   v = mock(Entity.class);
		Location l = new Location(null, 0, 0, 0);

		assertSame(CarEnterEvent.getHandlerList(), new CarEnterEvent(p, v, "x").getHandlers());
		assertSame(CarExitEvent.getHandlerList(), new CarExitEvent(p, v, false).getHandlers());
		assertSame(CarMoveEvent.getHandlerList(), new CarMoveEvent(p, v, l, l, 0).getHandlers());
		assertSame(CarDamageEvent.getHandlerList(), new CarDamageEvent(v, null, 0).getHandlers());
		assertSame(CarTheftAttemptEvent.getHandlerList(), new CarTheftAttemptEvent(p, v, null).getHandlers());

		Set<HandlerList> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
		distinct.add(CarEnterEvent.getHandlerList());
		distinct.add(CarExitEvent.getHandlerList());
		distinct.add(CarMoveEvent.getHandlerList());
		distinct.add(CarDamageEvent.getHandlerList());
		distinct.add(CarTheftAttemptEvent.getHandlerList());
		assertEquals(5, distinct.size());
	}
}
