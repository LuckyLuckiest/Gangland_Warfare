package org.luckyraven.gangland.gadget.listener.grapple;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.grapple.GrappleService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link GrappleFlightListener}: the allow-flight a taut rope lends (only so a hanging player is not kicked for
 * floating) can never be used to actually fly, and a join heals a marker left behind by a crash mid-swing.
 */
@DisplayName("GrappleFlightListener — borrowed flight stays borrowed")
class GrappleFlightListenerTest {

	private static PlayerToggleFlightEvent toggle(Player player, boolean flying) {
		return new PlayerToggleFlightEvent(player, flying);
	}

	@Test
	@DisplayName("starting to fly on a rope that lent the flight is cancelled")
	void toggleFlight_onBorrowedFlight_cancelled() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);
		when(service.holdsFlight(player)).thenReturn(true);

		PlayerToggleFlightEvent event = toggle(player, true);
		new GrappleFlightListener(service).onToggleFlight(event);

		assertTrue(event.isCancelled());
	}

	@Test
	@DisplayName("flight the rope did not lend (creative, jetpack) is left alone")
	void toggleFlight_ownFlight_untouched() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);

		PlayerToggleFlightEvent event = toggle(player, true);
		new GrappleFlightListener(service).onToggleFlight(event);

		assertFalse(event.isCancelled());
	}

	@Test
	@DisplayName("a join hands the player to healFlight")
	void join_healsFlight() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);

		new GrappleFlightListener(service).onJoin(new PlayerJoinEvent(player, "joined"));

		verify(service).healFlight(player);
	}
}
