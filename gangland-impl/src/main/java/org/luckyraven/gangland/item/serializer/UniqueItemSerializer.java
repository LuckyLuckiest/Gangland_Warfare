package org.luckyraven.gangland.item.serializer;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.gangland.item.ItemSerializer;
import org.luckyraven.gangland.item.unique.UniqueItemKeys;

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
