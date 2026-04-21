package me.luckyraven.item.serializer;

import me.luckyraven.core.ItemBuilder;
import me.luckyraven.item.ItemKind;
import me.luckyraven.item.ItemSerializer;
import me.luckyraven.weapon.ammo.Ammunition;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts the ammunition registry name from {@link Ammunition#NBT_KEY}.
 */
public final class AmmunitionItemSerializer implements ItemSerializer {

	@Override
	public ItemKind kind() {
		return ItemKind.AMMUNITION;
	}

	@Override
	@Nullable
	public String extract(ItemStack stack) {
		return new ItemBuilder(stack).getStringTagData(Ammunition.NBT_KEY);
	}
}
