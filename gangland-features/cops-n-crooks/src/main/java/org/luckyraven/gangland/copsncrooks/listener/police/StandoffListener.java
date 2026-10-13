package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.standoff.StandoffController;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Feeds cop deaths to the standoff's man-down trigger (0.21.0). TDD skeleton.
 *
 * @since 0.21.0
 */
@ListenerHandler
public class StandoffListener implements Listener {

	private final StandoffController controller;

	public StandoffListener(StandoffController controller) {
		this.controller = controller;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCopDeath(CopDeathEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
