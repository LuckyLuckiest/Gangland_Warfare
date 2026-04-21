package me.luckyraven.listener.bridge;

import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.events.user.UserDataInitEvent;
import me.luckyraven.item.event.PlayerItemInitEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

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
