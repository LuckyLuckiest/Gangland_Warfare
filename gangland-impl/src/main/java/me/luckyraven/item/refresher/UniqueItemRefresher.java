package me.luckyraven.item.refresher;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemRefresher;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.unique.UniqueItem;
import me.luckyraven.item.unique.UniqueItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Rebuilds a unique item (phone, repair kit, one-off gadget, etc.) into a factory-fresh copy. For consumable-style
 * unique items this restores any internal "uses remaining" counters stored in their build-time NBT.
 */
@RequiredArgsConstructor
public class UniqueItemRefresher implements ItemRefresher {

	private final UniqueItemAddon uniqueItemAddon;

	@Override
	public boolean canRefresh(ItemStack source) {
		return UniqueItemUtil.isUniqueItem(source);
	}

	@Override
	@Nullable
	public ItemStack refresh(ItemStack source, @Nullable Player context) {
		String key = UniqueItemUtil.getUniqueItemKey(source);
		if (key == null || key.isEmpty()) return null;

		UniqueItem unique = uniqueItemAddon.getUniqueItem(key);
		if (unique == null) return null;

		ItemStack built = context != null ? unique.buildItem(context) : unique.buildItem();
		if (built == null) return null;

		built.setAmount(source.getAmount());
		return built;
	}

}
