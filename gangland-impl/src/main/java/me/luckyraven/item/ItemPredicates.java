package me.luckyraven.item;

import me.luckyraven.gadget.car.CarKey;
import me.luckyraven.gadget.repair.RepairKeys;
import me.luckyraven.item.money.MoneyItemUtil;
import me.luckyraven.item.unique.UniqueItemKeys;
import me.luckyraven.item.wearable.Wearable;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponTag;
import me.luckyraven.weapon.ammo.Ammunition;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.function.Predicate;

/**
 * Constant {@link Predicate}s that identify each {@link ItemKind} by inspecting an {@link ItemStack}'s NBT. Centralises
 * every NBT-key lookup in one place so that if a domain renames its marker tag we change it here and every serializer
 * registration picks it up automatically.
 *
 * <p>This class lives in {@code gangland-impl} because the predicates depend on constants from the feature modules
 * ({@code CarKey}, {@code RepairKeys}, …) that {@code gangland-item} cannot see. The matching {@link ItemKind} enum
 * stays in {@code gangland-item} because it holds nothing but labels.
 */
public final class ItemPredicates {

	public static final Predicate<ItemStack> UNIQUE     = stack -> hasTag(stack, UniqueItemKeys.UNIQUE_ITEM_KEY);
	public static final Predicate<ItemStack> WEAPON     = stack -> hasTag(stack,
	                                                                      Weapon.getTagProperName(WeaponTag.WEAPON));
	public static final Predicate<ItemStack> AMMUNITION = stack -> hasTag(stack, Ammunition.NBT_KEY);
	public static final Predicate<ItemStack> WEARABLE   = stack -> hasTag(stack, Wearable.NBT_KEY);
	public static final Predicate<ItemStack> CAR        = stack -> hasTag(stack, CarKey.CAR_ID.getKey());
	public static final Predicate<ItemStack> MONEY      = stack -> hasTag(stack, MoneyItemUtil.MARKER_TAG);
	public static final Predicate<ItemStack> REPAIR     = stack -> hasTag(stack, RepairKeys.REPAIR_MATERIAL_ID);

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
