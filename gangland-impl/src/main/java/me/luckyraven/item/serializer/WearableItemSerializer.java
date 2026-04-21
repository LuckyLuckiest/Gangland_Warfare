package me.luckyraven.item.serializer;

import me.luckyraven.core.ItemBuilder;
import me.luckyraven.item.ItemKind;
import me.luckyraven.item.ItemSerializer;
import me.luckyraven.item.wearable.Wearable;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

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
