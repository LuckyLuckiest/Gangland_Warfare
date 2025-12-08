package me.luckyraven.listener.inventory;

import me.luckyraven.file.configuration.inventory.InventoryAddon;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

@ListenerHandler
public class UniqueItemInventoryRestrict implements Listener {

	@EventHandler(priority = EventPriority.LOW)
	public void onUniqueItemInventoryClick(InventoryClickEvent event) {
		if (event.getClickedInventory() == null) return;

		Inventory clickedInventory = event.getClickedInventory();
		ItemStack clickedItem      = event.getCurrentItem();

		if (clickedItem == null || clickedItem.getType().name().contains("AIR") || clickedItem.getAmount() == 0) return;
		if (!clickedInventory.equals(event.getWhoClicked().getInventory())) return;

		// Check if it's a unique item
		ItemBuilder itemBuilder = new ItemBuilder(clickedItem);
		if (!itemBuilder.hasNBTTag("uniqueItem")) return;

		var uniqueItemKey = itemBuilder.getStringTagData("uniqueItem");
		var handler       = InventoryAddon.getUniqueItemHandler(uniqueItemKey);

		if (handler == null) return;
		if (handler.isMovable()) return;

		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onUniqueItemDrop(PlayerDropItemEvent event) {
		ItemStack droppedItem = event.getItemDrop().getItemStack();

		// Check if it's a unique item
		ItemBuilder itemBuilder = new ItemBuilder(droppedItem);
		if (!itemBuilder.hasNBTTag("uniqueItem")) return;

		var uniqueItemKey = itemBuilder.getStringTagData("uniqueItem");
		var handler       = InventoryAddon.getUniqueItemHandler(uniqueItemKey);

		if (handler == null) return;
		if (handler.isDroppable()) return;

		event.setCancelled(true);
	}

}
