package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PerimeterController;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** {@link PerimeterListener}: SEARCHING sets the perimeter, SEEN sends the posts after him, OFF sends them home. */
@DisplayName("PerimeterListener")
class PerimeterListenerTest {

	private final PerimeterController controller = mock(PerimeterController.class);
	private final PerimeterListener   listener   = new PerimeterListener(controller);
	private final Player              player     = mock(Player.class);
	private final Location            centre     = new Location(null, 1, 64, 2);

	private WantedEvasionStateEvent event(EvasionState state, Location at) {
		return new WantedEvasionStateEvent(player, state, 4, 30, at, 90.0);
	}

	@Test
	@DisplayName("SEARCHING with a centre starts the perimeter around it")
	void searching_startsThePerimeter() {
		listener.onEvasionState(event(EvasionState.SEARCHING, centre));

		verify(controller).start(player, centre, 90.0, 4);
	}

	@Test
	@DisplayName("SEARCHING without a centre starts nothing")
	void searchingWithoutACentre_startsNothing() {
		listener.onEvasionState(event(EvasionState.SEARCHING, null));

		verify(controller, never()).start(any(), any(), anyDouble(), anyInt());
	}

	@Test
	@DisplayName("SEEN ends the perimeter with every post pursuing")
	void seen_endsWithPursuing() {
		listener.onEvasionState(event(EvasionState.SEEN, centre));

		verify(controller).end(player, CopState.PURSUING);
	}

	@Test
	@DisplayName("OFF ends the perimeter with every post returning")
	void off_endsWithReturning() {
		listener.onEvasionState(event(EvasionState.OFF, null));

		verify(controller).end(player, CopState.RETURNING);
	}

	@Test
	@DisplayName("EVADED does not touch the perimeter")
	void evaded_isIgnored() {
		listener.onEvasionState(event(EvasionState.EVADED, centre));

		verify(controller, never()).end(any(), any());
		verify(controller, never()).start(any(), any(), anyDouble(), anyInt());
	}
}
