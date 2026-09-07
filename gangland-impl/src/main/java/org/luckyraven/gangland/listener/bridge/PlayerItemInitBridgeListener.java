package org.luckyraven.gangland.listener.bridge;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.events.user.UserDataInitEvent;
import org.luckyraven.gangland.item.event.PlayerItemInitEvent;

/**
 * One-line bridge that re-fires {@link UserDataInitEvent} as a {@link PlayerItemInitEvent}. The
 * {@code PlayerItemInitEvent} lives in gangland-item so listeners over there can react to "user data is ready" without
 * importing the impl-side {@link UserDataInitEvent}.
 */
@ListenerHandler
public class PlayerItemInitBridgeListener implements Listener {

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onUserDataInit(UserDataInitEvent event) {
		Bukkit.getPluginManager().callEvent(new PlayerItemInitEvent(event.getPlayer(), event.isAsynchronous()));
	}

}
