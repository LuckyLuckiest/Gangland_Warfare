package org.luckyraven.gangland.copsncrooks.listener.police;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.events.teleportation.TeleportEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * CJ-43: a teleport warm-up that started before an arrest must not carry the cuffed or jailed player out when it
 * ends. The warm-up fires the api {@link TeleportEvent} just before moving the player; cancelling it keeps them put.
 */
@ListenerHandler
@RequiredArgsConstructor
public class RestrainedTeleportListener implements Listener {

	private final DetainmentService detainmentService;

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onTeleport(TeleportEvent event) {
		if (detainmentService.isRestrained(event.getUser().getUser())) {
			event.setCancelled(true);
		}
	}
}
