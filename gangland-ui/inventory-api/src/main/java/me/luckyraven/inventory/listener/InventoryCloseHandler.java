package me.luckyraven.inventory.listener;

import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.service.InventoryRegistry;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

@ListenerHandler
public class InventoryCloseHandler implements Listener {

	@EventHandler(priority = EventPriority.MONITOR)
	public void onInventoryClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player)) return;

		InventoryHandler inv = InventoryRegistry.getInstance().findByInventory(event.getView().getTopInventory());

		if (inv != null) {
			inv.unregister();
		}
	}

}
