package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.luckyraven.gangland.copsncrooks.wanted.bribe.BribeStars;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Hoppers and hopper minecarts ignore an item's pickup delay, so a floating bribe star is guarded here.
 *
 * @since 0.16.0
 */
@ListenerHandler
public class BribeStarListener implements Listener {

	private final BribeStars stars;

	public BribeStarListener(BribeStars stars) {
		this.stars = stars;
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onHopperPickup(InventoryPickupItemEvent event) {
		if (stars.isStar(event.getItem())) event.setCancelled(true);
	}
}
