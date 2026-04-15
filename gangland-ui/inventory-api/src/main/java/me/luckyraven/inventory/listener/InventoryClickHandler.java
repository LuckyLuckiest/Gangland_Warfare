package me.luckyraven.inventory.listener;

import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.service.InventoryRegistry;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;

@ListenerHandler
public class InventoryClickHandler implements Listener {

	private final InventoryRegistry inventoryRegistry;

	public InventoryClickHandler(InventoryRegistry inventoryRegistry) {
		this.inventoryRegistry = inventoryRegistry;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInventoryClick(InventoryClickEvent event) {
		var clickedInventory = event.getClickedInventory();
		var player           = (Player) event.getWhoClicked();
		var topInventory     = event.getView().getTopInventory();

		InventoryHandler inv = inventoryRegistry.findByInventory(topInventory);

		if (inv == null || clickedInventory == null) return;

		int rawSlot = event.getRawSlot();

		if (!clickedInventory.equals(inv.getInventory())) {
			InventoryAction action = event.getAction();

			if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
			    action == InventoryAction.COLLECT_TO_CURSOR) {
				event.setCancelled(true);
			}

			return;
		}

		var clickableItems = inv.getClickableItems();
		var itemBuilder    = clickableItems.getOrDefault(rawSlot, null);

		if (event.isRightClick()) {
			var rightClickSlots  = inv.getRightClickSlots();
			var rightClickAction = rightClickSlots.getOrDefault(rawSlot, null);

			if (rightClickAction != null) {
				rightClickAction.accept(player, inv, itemBuilder);
				event.setCancelled(!inv.getDraggableSlots().contains(rawSlot));
				return;
			}
		}

		var clickableSlots = inv.getClickableSlots();
		var action         = clickableSlots.getOrDefault(rawSlot, (pl, i, item) -> { });

		action.accept(player, inv, itemBuilder);

		event.setCancelled(!inv.getDraggableSlots().contains(rawSlot));
	}

}
