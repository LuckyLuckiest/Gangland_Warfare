package org.luckyraven.gangland.copsncrooks.listener.standoff;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.standoff.StandoffController;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Clears a suspect's standoff state when he quits mid-standoff (0.21.0 judge-fix C3).
 *
 * @since 0.21.0
 */
@ListenerHandler
public class StandoffListener implements Listener {

	private final StandoffController controller;

	public StandoffListener(StandoffController controller) {
		this.controller = controller;
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		controller.forget(event.getPlayer().getUniqueId());
	}
}
