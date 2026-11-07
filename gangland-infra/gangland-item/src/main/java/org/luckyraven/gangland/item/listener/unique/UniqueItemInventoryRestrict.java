package org.luckyraven.gangland.item.listener.unique;

import lombok.RequiredArgsConstructor;
import org.bukkit.GameRule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.item.contract.UniqueItemRegistry;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.item.unique.UniqueItemUtil;

@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({UniqueItemRegistry.class})
public class UniqueItemInventoryRestrict implements Listener {

	private final UniqueItemRegistry uniqueItemAddon;

	@EventHandler(priority = EventPriority.LOW)
	public void onUniqueItemInventoryClick(InventoryClickEvent event) {
		Inventory clickedInventory = event.getClickedInventory();

		if (clickedInventory == null) return;

		// a number-key or off-hand swap moves the hotbar/off-hand stack, whichever inventory the clicked slot is in
		if (lockedInPlace(swapPartner(event))) {
			event.setCancelled(true);
			return;
		}

		ItemStack clickedItem = event.getCurrentItem();

		// check the cursor item (for drag operations)
		if (clickedItem == null) {
			clickedItem = event.getCursor();
		}

		if (clickedItem == null || clickedItem.getType().name().contains("AIR") || clickedItem.getAmount() == 0) return;
		if (!clickedInventory.equals(event.getWhoClicked().getInventory())) return;

		// only restrict movement in player inventory
		if (clickedInventory.getType() != InventoryType.PLAYER) return;

		if (!lockedInPlace(clickedItem)) return;

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

			var uniqueItem = registered(item);

			return uniqueItem != null && !uniqueItem.isDropOnDeath();
		});
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onUniqueItemDrop(PlayerDropItemEvent event) {
		ItemStack droppedItem = event.getItemDrop().getItemStack();

		// Check if it's a unique item
		if (!UniqueItemUtil.isUniqueItem(droppedItem)) return;

		var uniqueItem = registered(droppedItem);

		if (uniqueItem == null) return;
		if (uniqueItem.isDroppable()) return;

		event.setCancelled(true);
	}

	/** The stack a number-key or off-hand swap click moves, or {@code null} for any other click. */
	private static ItemStack swapPartner(InventoryClickEvent event) {
		ClickType click = event.getClick();

		if (click == ClickType.NUMBER_KEY) return event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
		if (click == ClickType.SWAP_OFFHAND) return event.getWhoClicked().getInventory().getItemInOffHand();
		return null;
	}

	/** Whether the stack is a registered unique item with {@code Movable: false}. */
	private boolean lockedInPlace(ItemStack stack) {
		if (stack == null || stack.getType().name().contains("AIR") || stack.getAmount() == 0) return false;
		if (!UniqueItemUtil.isUniqueItem(stack)) return false;

		var uniqueItem = registered(stack);

		return uniqueItem != null && !uniqueItem.isMovable();
	}
	private UniqueItem registered(ItemStack stack) {
		var itemBuilder   = new ItemBuilder(stack);
		var uniqueItemKey = itemBuilder.getStringTagData("uniqueItem");
		return uniqueItemAddon.getUniqueItem(uniqueItemKey);
	}

}
