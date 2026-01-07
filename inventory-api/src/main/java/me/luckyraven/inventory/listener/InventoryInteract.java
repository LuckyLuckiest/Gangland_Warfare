package me.luckyraven.inventory.listener;

import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.service.InventoryRegistry;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

@ListenerHandler
public class InventoryInteract implements Listener {

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInventoryClick(InventoryClickEvent event) {
		var clickedInventory = event.getClickedInventory();
		var player           = (Player) event.getWhoClicked();
		var topInventory     = event.getView().getTopInventory();

		InventoryHandler inv = InventoryRegistry.getInstance().findByInventory(topInventory);

		int rawSlot = event.getRawSlot();

		// Execute clickable action if defined for clicks in the custom inventory
		boolean checkInventoryStatus = inv != null && clickedInventory != null &&
									   clickedInventory.equals(inv.getInventory());

		if (checkInventoryStatus) {
			var clickableItems = inv.getClickableItems();
			var itemBuilder    = clickableItems.getOrDefault(rawSlot, null);

			// Check if it's a right click
			if (event.isRightClick()) {
				var rightClickSlots  = inv.getRightClickSlots();
				var rightClickAction = rightClickSlots.getOrDefault(rawSlot, null);

				if (rightClickAction != null) {
					rightClickAction.accept(player, inv, itemBuilder);
					event.setCancelled(!inv.getDraggableSlots().contains(rawSlot));
					return;
				}
			}

			// Default to left click action (or any click if no right click handler)
			var clickableSlots = inv.getClickableSlots();
			var slots          = clickableSlots.getOrDefault(rawSlot, (pl, i, item) -> { });

			slots.accept(player, inv, itemBuilder);

			event.setCancelled(!inv.getDraggableSlots().contains(rawSlot));
		}
	}

}
