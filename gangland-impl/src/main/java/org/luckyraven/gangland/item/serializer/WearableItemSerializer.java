package org.luckyraven.gangland.item.serializer;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.ItemBuilder;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.gangland.item.ItemSerializer;
import org.luckyraven.gangland.item.wearable.Wearable;

/**
 * Extracts the wearable registry name from {@link Wearable#NBT_KEY}.
 */
public final class WearableItemSerializer implements ItemSerializer {

	@Override
	public ItemKind kind() {
		return ItemKind.WEARABLE;
	}

	@Override
	@Nullable
	public String extract(ItemStack stack) {
		return new ItemBuilder(stack).getStringTagData(Wearable.NBT_KEY);
	}
}
