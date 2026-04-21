package me.luckyraven.item.serializer;

import me.luckyraven.core.ItemBuilder;
import me.luckyraven.item.ItemKind;
import me.luckyraven.item.ItemSerializer;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponTag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

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
