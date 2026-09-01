package org.luckyraven.gangland.item.serializer;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.gadget.car.CarKey;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.gangland.item.ItemSerializer;

/**
 * Extracts the car registry id from {@link CarKey#CAR_ID}.
 */
public final class CarItemSerializer implements ItemSerializer {

	@Override
	public ItemKind kind() {
		return ItemKind.CAR;
	}

	@Override
	@Nullable
	public String extract(ItemStack stack) {
		return new ItemBuilder(stack).getStringTagData(CarKey.CAR_ID.getKey());
	}
}
