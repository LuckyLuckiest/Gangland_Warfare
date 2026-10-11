package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.events.teleportation.TeleportEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** CJ-43: a warm-up that started before an arrest must not teleport the cuffed or jailed player out. */
@DisplayName("RestrainedTeleportListener")
class RestrainedTeleportListenerTest {

	private final DetainmentService         detainment = mock(DetainmentService.class);
	private final RestrainedTeleportListener listener  = new RestrainedTeleportListener(detainment);

	@SuppressWarnings("unchecked")
	private TeleportEvent event(Player player) {
		User<Player> user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		return new TeleportEvent(user, null, mock(Waypoint.class));
	}

	@Test
	@DisplayName("restrained_cancelled: a handcuffed or jailed player's teleport is cancelled")
	void restrained_cancelled() {
		Player player = mock(Player.class);
		when(detainment.isRestrained(player)).thenReturn(true);
		TeleportEvent event = event(player);

		listener.onTeleport(event);

		assertTrue(event.isCancelled());
	}

	@Test
	@DisplayName("free_untouched: everyone else teleports as before")
	void free_untouched() {
		Player player = mock(Player.class);
		TeleportEvent event = event(player);

		listener.onTeleport(event);

		assertFalse(event.isCancelled());
	}
}
