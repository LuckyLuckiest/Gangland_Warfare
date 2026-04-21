package me.luckyraven.item.serializer;

import me.luckyraven.core.ItemBuilder;
import me.luckyraven.item.ItemKind;
import me.luckyraven.item.ItemSerializer;
import me.luckyraven.item.unique.UniqueItemKeys;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts the unique-item key written by {@code UniqueConverter}. Registered first so unique identity wins over any
 * wrapped domain (a unique weapon serialises as {@code unique:…}, not {@code weapon:…}).
 */
public final class UniqueItemSerializer implements ItemSerializer {

	@Override
	public ItemKind kind() {
		return ItemKind.UNIQUE;
	}

	@Override
	@Nullable
	public String extract(ItemStack stack) {
		return new ItemBuilder(stack).getStringTagData(UniqueItemKeys.UNIQUE_ITEM_KEY);
	}
}
