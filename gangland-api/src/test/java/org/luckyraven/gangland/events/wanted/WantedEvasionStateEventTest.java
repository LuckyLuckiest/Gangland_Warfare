package org.luckyraven.gangland.events.wanted;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;

class WantedEvasionStateEventTest {

	@Test
	@DisplayName("a listener mutating the zone centre does not move the clock's own centre")
	void zoneCentre_isACopy() {
		Location centre = new Location(null, 10, 64, 20);
		WantedEvasionStateEvent event = new WantedEvasionStateEvent(mock(Player.class), EvasionState.SEARCHING, 2, 10,
		                                                            centre, 40);

		Location handed = event.getZoneCentre();
		handed.setX(999);

		assertNotSame(centre, handed);
		assertEquals(10, centre.getX(), 1.0E-9);
	}
}
