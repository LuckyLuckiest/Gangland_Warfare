package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.npc.police.perch.OverwatchController;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Routes the evasion clock's state changes to the Marksman overwatch: out of sight (SEARCHING) starts it, OFF ends it.
 * SEEN does not end it (D-O2: he stays perched until the chase ends, the stars drop below the floor or Max_Seconds).
 *
 * @since 0.20.0
 */
@ListenerHandler
public class OverwatchListener implements Listener {

	private final OverwatchController controller;

	public OverwatchListener(OverwatchController controller) {
		this.controller = controller;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEvasionState(WantedEvasionStateEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
