package org.luckyraven.gangland.copsncrooks.detainment.breakfree;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.luckyraven.gangland.item.unique.UniqueItemUtil;

/**
 * Finds and consumes the lockpick unique item (key {@code lockpick}) in a player's inventory.
 *
 * @since 0.21.0
 */
public final class Lockpicks {

	public static final String ITEM_KEY = "lockpick";

	private Lockpicks() {
	}

	public static boolean has(Player player) {
		PlayerInventory inventory = inventoryOf(player);
		return inventory != null && pickSlot(inventory.getContents()) >= 0;
	}

	public static boolean consumeOne(Player player) {
		PlayerInventory inventory = inventoryOf(player);
		if (inventory == null) return false;

		ItemStack[] contents = inventory.getContents();
		int         slot     = pickSlot(contents);
		if (slot < 0) return false;

		ItemStack stack = contents[slot];
		if (stack.getAmount() > 1) {
			stack.setAmount(stack.getAmount() - 1);
			inventory.setItem(slot, stack);
		} else {
			inventory.setItem(slot, null);
		}
		return true;
	}

	private static PlayerInventory inventoryOf(Player player) {
		return player == null ? null : player.getInventory();
	}

	private static int pickSlot(ItemStack[] contents) {
		if (contents == null) return -1;

		for (int i = 0; i < contents.length; i++) {
			ItemStack stack = contents[i];
			if (stack == null) continue;
			if (ITEM_KEY.equalsIgnoreCase(UniqueItemUtil.getUniqueItemKey(stack))) return i;
		}
		return -1;
	}
}
