package org.luckyraven.gangland.inventory.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.inventory.service.InventoryRegistry;

@ListenerHandler
public class PlayerInventoryCleanup implements Listener {

	private final InventoryRegistry inventoryRegistry;

	public PlayerInventoryCleanup(InventoryRegistry inventoryRegistry) {
		this.inventoryRegistry = inventoryRegistry;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		inventoryRegistry.clear(event.getPlayer().getUniqueId());
	}

}
