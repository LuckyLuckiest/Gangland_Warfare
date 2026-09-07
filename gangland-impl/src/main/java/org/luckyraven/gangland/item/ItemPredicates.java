package org.luckyraven.gangland.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.gadget.car.CarKey;
import org.luckyraven.gangland.item.money.MoneyItemUtil;
import org.luckyraven.gangland.item.unique.UniqueItemKeys;
import org.luckyraven.gangland.item.wearable.Wearable;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponTag;
import org.luckyraven.gangland.weapon.ammo.Ammunition;

import java.util.function.Predicate;

/**
 * Constant {@link Predicate}s that identify each {@link ItemKind} by inspecting an {@link ItemStack}'s NBT. Centralises
 * every NBT-key lookup in one place so that if a domain renames its marker tag we change it here and every serializer
 * registration picks it up automatically.
 *
 * <p>This class lives in {@code gangland-impl} because the predicates depend on constants from the feature modules
 * ({@code CarKey}, …) that {@code gangland-item} cannot see. The matching {@link ItemKind} enum stays in
 * {@code gangland-item} because it holds nothing but labels.
 */
public final class ItemPredicates {

	public static final Predicate<ItemStack> UNIQUE     = stack -> hasTag(stack, UniqueItemKeys.UNIQUE_ITEM_KEY);
	public static final Predicate<ItemStack> WEAPON     = stack -> hasTag(stack,
	                                                                      Weapon.getTagProperName(WeaponTag.WEAPON));
	public static final Predicate<ItemStack> AMMUNITION = stack -> hasTag(stack, Ammunition.NBT_KEY);
	public static final Predicate<ItemStack> WEARABLE   = stack -> hasTag(stack, Wearable.NBT_KEY);
	public static final Predicate<ItemStack> CAR        = stack -> hasTag(stack, CarKey.CAR_ID.getKey());
	public static final Predicate<ItemStack> MONEY      = stack -> hasTag(stack, MoneyItemUtil.MARKER_TAG);

	/**
	 * Trivial catch-all for the material fallback — matches any non-air stack.
	 */
	public static final Predicate<ItemStack> MATERIAL = stack -> stack != null && stack.getType() != Material.AIR;

	private ItemPredicates() {
	}

	private static boolean hasTag(ItemStack stack, String tag) {
		if (stack == null) {
			return false;
		}
		return new ItemBuilder(stack).hasNBTTag(tag);
	}
}
