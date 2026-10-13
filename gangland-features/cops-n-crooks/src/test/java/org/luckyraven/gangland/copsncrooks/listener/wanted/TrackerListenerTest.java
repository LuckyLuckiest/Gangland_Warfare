package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.TrackerTags;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link TrackerListener}: the chase ending or the player quitting clears the tracker's per-player state. */
@DisplayName("TrackerListener")
class TrackerListenerTest {

	private final TrackerTags     tags     = mock(TrackerTags.class);
	private final TrackerListener listener = new TrackerListener(tags);
	private final UUID            id       = UUID.randomUUID();
	private final Player          player   = mock(Player.class);

	@Test
	@DisplayName("WantedEndEvent forgets the player")
	void wantedEnd_forgets() {
		when(player.getUniqueId()).thenReturn(id);

		listener.onWantedEnd(new WantedEndEvent(player, mock(Wanted.class), WantedCause.EVASION));

		verify(tags).forget(id);
	}

	@Test
	@DisplayName("quit forgets the player")
	void quit_forgets() {
		when(player.getUniqueId()).thenReturn(id);

		listener.onQuit(new PlayerQuitEvent(player, "bye"));

		verify(tags).forget(id);
	}
}
