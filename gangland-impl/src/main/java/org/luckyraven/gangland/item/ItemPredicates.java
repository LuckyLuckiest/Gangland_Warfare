package org.luckyraven.gangland.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.item.money.MoneyItemUtil;
import org.luckyraven.gangland.item.unique.UniqueItemKeys;

import java.util.function.Predicate;

/**
 * Constant {@link Predicate}s that identify each {@link ItemKind} by inspecting an {@link ItemStack}'s NBT. Centralises
 * every NBT-key lookup in one place so that if a domain renames its marker tag we change it here and every serializer
 * registration picks it up automatically.
 *
 * <p>This class lives in {@code gangland-impl} because the predicates depend on constants from the feature modules
 * that {@code gangland-item} cannot see. The matching {@link ItemKind} enum stays in {@code gangland-item} because it
 * holds nothing but labels. The {@code CAR} predicate moved to the gadget module's {@code GadgetItemPredicates}, and
 * the {@code WEAPON}/{@code AMMUNITION}/{@code WEARABLE} predicates left with the weapon module (0.8.4) and then with
 * Bartizan (0.9.0) — the core no longer names {@code Weapon} / {@code Ammunition} / {@code Wearable} at all.
 */
public final class ItemPredicates {

	public static final Predicate<ItemStack> UNIQUE     = stack -> hasTag(stack, UniqueItemKeys.UNIQUE_ITEM_KEY);
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
