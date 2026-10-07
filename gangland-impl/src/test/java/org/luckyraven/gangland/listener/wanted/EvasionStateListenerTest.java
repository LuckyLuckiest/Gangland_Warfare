package org.luckyraven.gangland.listener.wanted;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.wanted.ContactDesk;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The evasion-state event feeds the desk and a quit drops the sighting. */
@DisplayName("EvasionStateListener")
class EvasionStateListenerTest {

	private final ContactDesk          desk     = new ContactDesk(() -> 0L);
	private final Player               player   = mock(Player.class);
	private final UUID                 id       = UUID.randomUUID();
	private final EvasionStateListener listener = new EvasionStateListener(desk);

	EvasionStateListenerTest() {
		when(player.getUniqueId()).thenReturn(id);
	}

	@Test
	@DisplayName("SEEN marks the player seen, SEARCHING and OFF clear it")
	void state_feedsTheDesk() {
		listener.onState(new WantedEvasionStateEvent(player, EvasionState.SEEN, 2, 0, null, 0));
		assertTrue(desk.seen(id));

		listener.onState(new WantedEvasionStateEvent(player, EvasionState.SEARCHING, 2, 30, null, 0));
		assertFalse(desk.seen(id));

		listener.onState(new WantedEvasionStateEvent(player, EvasionState.SEEN, 2, 0, null, 0));
		listener.onState(new WantedEvasionStateEvent(player, EvasionState.OFF, 0, 0, null, 0));
		assertFalse(desk.seen(id));
	}

	@Test
	@DisplayName("a quit forgets the sighting")
	void quit_forgets() {
		listener.onState(new WantedEvasionStateEvent(player, EvasionState.SEEN, 2, 0, null, 0));

		listener.onQuit(new PlayerQuitEvent(player, "bye"));

		assertFalse(desk.seen(id));
	}

}
