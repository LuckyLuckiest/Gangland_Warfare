package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.perch.OverwatchController;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** {@link OverwatchListener}: SEARCHING starts the overwatch, OFF brings him down, SEEN does not (D-O2). */
@DisplayName("OverwatchListener")
class OverwatchListenerTest {

	private final OverwatchController controller = mock(OverwatchController.class);
	private final OverwatchListener   listener   = new OverwatchListener(controller);
	private final Player              player     = mock(Player.class);
	private final Location            centre     = new Location(null, 1, 64, 2);

	private WantedEvasionStateEvent event(EvasionState state, int level, Location at) {
		return new WantedEvasionStateEvent(player, state, level, 30, at, 90.0);
	}

	@Test
	@DisplayName("SEARCHING with a centre starts the overwatch at the last known spot with the star level")
	void searching_startsTheOverwatch() {
		listener.onEvasionState(event(EvasionState.SEARCHING, 4, centre));

		verify(controller).start(player, centre, 4);
	}

	@Test
	@DisplayName("SEARCHING without a centre starts nothing")
	void searchingWithoutACentre_startsNothing() {
		listener.onEvasionState(event(EvasionState.SEARCHING, 4, null));

		verify(controller, never()).start(any(), any(), anyInt());
	}

	@Test
	@DisplayName("SEEN does not end the overwatch (D-O2)")
	void seenDoesNotEnd() {
		listener.onEvasionState(event(EvasionState.SEEN, 4, centre));

		verifyNoInteractions(controller);
	}

	@Test
	@DisplayName("OFF sends the Marksman home")
	void off_endsWithReturning() {
		listener.onEvasionState(event(EvasionState.OFF, 0, null));

		verify(controller).end(player, CopState.RETURNING);
	}

	@Test
	@DisplayName("EVADED touches nothing")
	void evaded_isIgnored() {
		listener.onEvasionState(event(EvasionState.EVADED, 4, centre));

		verifyNoInteractions(controller);
	}
}
