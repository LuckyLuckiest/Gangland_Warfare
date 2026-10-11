package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.events.teleportation.TeleportEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** RED stub. */
@ListenerHandler
public class RestrainedTeleportListener implements Listener {

	public RestrainedTeleportListener(DetainmentService detainmentService) {
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onTeleport(TeleportEvent event) {
	}
}
