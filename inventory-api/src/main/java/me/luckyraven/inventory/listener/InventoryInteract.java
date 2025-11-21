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
			var clickableSlots = inv.getClickableSlots();
			var slots          = clickableSlots.getOrDefault(rawSlot, (pl, i, item) -> { });

			var clickableItems = inv.getClickableItems();
			slots.accept(player, inv, clickableItems.getOrDefault(rawSlot, null));
		}

		// Cancel all clicks to prevent item movement
		// Only allow if it's a click in the custom inventory AND the slot is draggable
		if (checkInventoryStatus) {
			event.setCancelled(!inv.getDraggableSlots().contains(rawSlot));
		} else {
			// Always cancel clicks in the player's own inventory when viewing custom inventory
			event.setCancelled(true);
		}
	}

}
