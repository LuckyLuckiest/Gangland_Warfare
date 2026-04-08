package me.luckyraven.item.listener.unique;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.contract.UniqueItemRegistry;
import me.luckyraven.item.unique.UniqueItemUtil;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.GameRule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({UniqueItemRegistry.class})
public class UniqueItemInventoryRestrict implements Listener {

	private final UniqueItemRegistry uniqueItemAddon;

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
		var uniqueItem    = uniqueItemAddon.getUniqueItem(uniqueItemKey);

		if (uniqueItem == null) return;
		if (uniqueItem.isMovable()) return;

		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onUniqueItemDeathDrop(PlayerDeathEvent event) {
		if (Boolean.TRUE.equals(event.getEntity().getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY))) {
			event.getDrops().removeIf(UniqueItemUtil::isUniqueItem);
			return;
		}

		event.getDrops().removeIf(item -> {
			if (!UniqueItemUtil.isUniqueItem(item)) return false;

			var itemBuilder   = new ItemBuilder(item);
			var uniqueItemKey = itemBuilder.getStringTagData("uniqueItem");
			var uniqueItem    = uniqueItemAddon.getUniqueItem(uniqueItemKey);

			return uniqueItem != null && !uniqueItem.isDropOnDeath();
		});
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onUniqueItemDrop(PlayerDropItemEvent event) {
		ItemStack droppedItem = event.getItemDrop().getItemStack();

		// Check if it's a unique item
		if (!UniqueItemUtil.isUniqueItem(droppedItem)) return;

		var itemBuilder   = new ItemBuilder(droppedItem);
		var uniqueItemKey = itemBuilder.getStringTagData("uniqueItem");
		var uniqueItem    = uniqueItemAddon.getUniqueItem(uniqueItemKey);

		if (uniqueItem == null) return;
		if (uniqueItem.isDroppable()) return;

		event.setCancelled(true);
	}

}
