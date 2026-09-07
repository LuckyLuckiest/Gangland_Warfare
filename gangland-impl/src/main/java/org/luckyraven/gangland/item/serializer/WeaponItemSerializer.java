package org.luckyraven.gangland.item.serializer;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.gangland.item.ItemSerializer;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponTag;

/**
 * Extracts the weapon registry name from the canonical weapon NBT tag written by {@code WeaponConverter}.
 */
public final class WeaponItemSerializer implements ItemSerializer {

	@Override
	public ItemKind kind() {
		return ItemKind.WEAPON;
	}

	@Override
	@Nullable
	public String extract(ItemStack stack) {
		return new ItemBuilder(stack).getStringTagData(Weapon.getTagProperName(WeaponTag.WEAPON));
	}
}
