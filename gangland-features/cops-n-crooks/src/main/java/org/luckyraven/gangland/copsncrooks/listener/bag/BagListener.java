package org.luckyraven.gangland.copsncrooks.listener.bag;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.bag.BagEnv;
import org.luckyraven.gangland.copsncrooks.bag.BagService;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.events.user.UserDataInitEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Wires the bag to the server: wanted endings, quit, the join settle and the hot-bill pickup star.
 *
 * @since 0.20.0
 */
@ListenerHandler
public class BagListener implements Listener {

	public BagListener(BagService service, BagEnv env) {
	}

	/** HIGH: after 0.21's report at NORMAL, before its Getaway.Line at MONITOR (D-W2-28). */
	@EventHandler(priority = EventPriority.HIGH)
	public void onWantedEnd(WantedEndEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onQuit(PlayerQuitEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Never PlayerJoinEvent: wanted is restored after the async user load (D-W2-31). */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onUserData(UserDataInitEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** NORMAL, before the core HIGH pickup listener; never cancels. */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPickup(EntityPickupItemEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
