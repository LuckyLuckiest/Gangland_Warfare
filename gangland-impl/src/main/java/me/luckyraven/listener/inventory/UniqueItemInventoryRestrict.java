package me.luckyraven.listener.inventory;

import lombok.RequiredArgsConstructor;
import me.luckyraven.Gangland;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.item.unique.UniqueItemUtil;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

@ListenerHandler
@RequiredArgsConstructor
public class UniqueItemInventoryRestrict implements Listener {

	private final Gangland gangland;

	@EventHandler(priority = EventPriority.LOW)
	public void onUniqueItemInventoryClick(InventoryClickEvent event) {
		Inventory clickedInventory = event.getClickedInventory();

		if (clickedInventory == null) return;

		ItemStack clickedItem = event.getCurrentItem();

		// check the cursor item (for drag operations)
		if (clickedItem == null) {
			clickedItem = event.getCursor();
		}

		if (clickedItem == null || clickedItem.getType().name().contains("AIR") || clickedItem.getAmount() == 0) return;
		if (!clickedInventory.equals(event.getWhoClicked().getInventory())) return;

		// only restrict movement in player inventory
		if (clickedInventory.getType() != InventoryType.PLAYER) return;

		// Check if it's a unique item
		if (!UniqueItemUtil.isUniqueItem(clickedItem)) return;

		var itemBuilder   = new ItemBuilder(clickedItem);
		var uniqueItemKey = itemBuilder.getStringTagData("uniqueItem");
		var uniqueItem    = gangland.getInitializer().getUniqueItemAddon().getUniqueItem(uniqueItemKey);

		if (uniqueItem == null) return;
		if (uniqueItem.isMovable()) return;

		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onUniqueItemDrop(PlayerDropItemEvent event) {
		ItemStack droppedItem = event.getItemDrop().getItemStack();

		// Check if it's a unique item
		if (!UniqueItemUtil.isUniqueItem(droppedItem)) return;

		var itemBuilder   = new ItemBuilder(droppedItem);
		var uniqueItemKey = itemBuilder.getStringTagData("uniqueItem");
		var uniqueItem    = gangland.getInitializer().getUniqueItemAddon().getUniqueItem(uniqueItemKey);

		if (uniqueItem == null) return;
		if (uniqueItem.isDroppable()) return;

		event.setCancelled(true);
	}

}
