package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PerimeterController;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Routes the evasion clock's state changes to the containment perimeter: out of sight (SEARCHING) sets it, SEEN sends
 * every post after the suspect, OFF sends them home.
 *
 * @since 0.16.0
 */
@ListenerHandler
public class PerimeterListener implements Listener {

	private final PerimeterController controller;

	public PerimeterListener(PerimeterController controller) {
		this.controller = controller;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEvasionState(WantedEvasionStateEvent event) {
		EvasionState state = event.getState();
		if (state == EvasionState.SEARCHING) {
			Location centre = event.getZoneCentre();
			if (centre != null) controller.start(event.getPlayer(), centre, event.getZoneRadius(), event.getLevel());
		} else if (state == EvasionState.SEEN) {
			controller.end(event.getPlayer(), CopState.PURSUING);
		} else if (state == EvasionState.OFF) {
			controller.end(event.getPlayer(), CopState.RETURNING);
		}
	}
}
