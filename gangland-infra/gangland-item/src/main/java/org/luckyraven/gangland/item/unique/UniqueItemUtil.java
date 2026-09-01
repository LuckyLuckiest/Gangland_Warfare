package org.luckyraven.gangland.item.unique;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;

public final class UniqueItemUtil {

	public static boolean isUniqueItem(@Nullable ItemStack itemStack) {
		if (itemStack == null || itemStack.getType() == Material.AIR) return false;

		return new ItemBuilder(itemStack).hasNBTTag(UniqueItemKeys.UNIQUE_ITEM_KEY);
	}

	@Nullable
	public static String getUniqueItemKey(@Nullable ItemStack itemStack) {
		if (!isUniqueItem(itemStack)) return null;

		return new ItemBuilder(itemStack).getStringTagData(UniqueItemKeys.UNIQUE_ITEM_KEY);
	}

	public static boolean hasUniqueItem(Player player, UniqueItem uniqueItem) {
		ItemStack[] contents = player.getInventory().getContents();

		for (ItemStack item : contents) {
			if (item == null) continue;
			if (item.getType() != uniqueItem.getMaterial()) continue;
			if (!isUniqueItem(item)) continue;
			if (uniqueItem.compareTo(item) == 0) continue;

			return true;
		}

		return false;
	}

}
