package me.luckyraven.util.item.unique;

import me.luckyraven.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class UniqueItemUtil {

	public static boolean isUniqueItem(ItemStack itemStack) {
		ItemBuilder itemBuilder = new ItemBuilder(itemStack);

		return itemBuilder.hasNBTTag("uniqueItem");
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
