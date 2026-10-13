package org.luckyraven.gangland.copsncrooks.listener.surrender;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.detainment.surrender.SurrenderService;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * 0.21.0 Surrender judge-fix r2: a quitter's surrender, resisted and too-hot memory is pruned so nothing outlives him.
 */
@ListenerHandler
public class SurrenderQuitListener implements Listener {

	private final SurrenderService service;

	public SurrenderQuitListener(SurrenderService service) {
		this.service = service;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		service.forget(event.getPlayer().getUniqueId());
	}
}
