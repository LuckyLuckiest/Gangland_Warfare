package org.luckyraven.gangland.weapon.item;

import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponTag;
import org.luckyraven.gangland.weapon.ammo.Ammunition;

import java.util.function.Predicate;

/**
 * Constant {@link Predicate}s that identify a weapon or ammunition {@link ItemStack} by its NBT. Moved out of the
 * core's {@code ItemPredicates} when the weapon module was split out — the core cannot see {@code Weapon} or
 * {@code Ammunition} any more. {@code ItemPredicates.WEARABLE} stays in core (it depends only on
 * {@code org.luckyraven.gangland.item.wearable.Wearable}, a gangland-item type) and this module imports it from
 * there instead of duplicating it here.
 */
public final class WeaponItemPredicates {

	public static final Predicate<ItemStack> WEAPON     = stack -> hasTag(stack,
	                                                                      Weapon.getTagProperName(WeaponTag.WEAPON));
	public static final Predicate<ItemStack> AMMUNITION = stack -> hasTag(stack, Ammunition.NBT_KEY);

	private WeaponItemPredicates() {
	}

	private static boolean hasTag(ItemStack stack, String tag) {
		if (stack == null) {
			return false;
		}
		return new ItemBuilder(stack).hasNBTTag(tag);
	}
}
