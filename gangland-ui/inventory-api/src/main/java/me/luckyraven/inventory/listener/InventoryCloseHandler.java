package me.luckyraven.inventory.listener;

import me.luckyraven.core.listener.ListenerHandler;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.service.InventoryRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

@ListenerHandler
public class InventoryCloseHandler implements Listener {

	private final InventoryRegistry inventoryRegistry;

	public InventoryCloseHandler(InventoryRegistry inventoryRegistry) {
		this.inventoryRegistry = inventoryRegistry;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onInventoryClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player)) return;

		InventoryHandler inv = inventoryRegistry.findByInventory(event.getView().getTopInventory());

		if (inv != null) {
			inv.unregister();
		}
	}

}
