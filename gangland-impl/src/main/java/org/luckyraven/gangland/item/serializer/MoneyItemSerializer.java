package org.luckyraven.gangland.item.serializer;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.gangland.item.ItemSerializer;
import org.luckyraven.gangland.item.money.MoneyItemUtil;

/**
 * Extracts the money variation name from {@link MoneyItemUtil#VARIATION_TAG}. When a money stack carries the marker tag
 * but no variation, we fall back to {@code "default"} so the ledger still distinguishes money from raw materials.
 */
public final class MoneyItemSerializer implements ItemSerializer {

	@Override
	public ItemKind kind() {
		return ItemKind.MONEY;
	}

	@Override
	@Nullable
	public String extract(ItemStack stack) {
		String variation = new ItemBuilder(stack).getStringTagData(MoneyItemUtil.VARIATION_TAG);
		if (variation == null || variation.isEmpty()) {
			return "default";
		}
		return variation;
	}
}
