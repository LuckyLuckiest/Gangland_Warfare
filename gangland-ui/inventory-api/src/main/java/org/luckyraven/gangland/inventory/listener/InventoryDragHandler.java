package org.luckyraven.gangland.inventory.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.service.InventoryRegistry;

import java.util.List;

@ListenerHandler
public class InventoryDragHandler implements Listener {

	private final InventoryRegistry inventoryRegistry;

	public InventoryDragHandler(InventoryRegistry inventoryRegistry) {
		this.inventoryRegistry = inventoryRegistry;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInventoryDrag(InventoryDragEvent event) {
		if (!(event.getWhoClicked() instanceof Player)) return;

		var topInventory = event.getView().getTopInventory();

		InventoryHandler inv = inventoryRegistry.findByInventory(topInventory);

		if (inv == null) return;

		List<Integer> draggableSlots = inv.getDraggableSlots();
		int           inventorySize  = topInventory.getSize();

		for (int rawSlot : event.getRawSlots()) {
			if (rawSlot < inventorySize && !draggableSlots.contains(rawSlot)) {
				event.setCancelled(true);
				return;
			}
		}
	}

}
