package me.luckyraven.item.serializer;

import me.luckyraven.core.ItemBuilder;
import me.luckyraven.gadget.car.CarKey;
import me.luckyraven.item.ItemKind;
import me.luckyraven.item.ItemSerializer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

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
