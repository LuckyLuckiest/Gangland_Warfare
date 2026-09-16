package org.luckyraven.gangland.gadget.item;

import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.gadget.car.CarKey;
import org.luckyraven.gangland.gadget.jetpack.JetpackKey;
import org.luckyraven.keystone.item.ItemBuilder;

import java.util.function.Predicate;

/**
 * Module-side counterpart of the core's {@code ItemPredicates}: constant {@link Predicate}s that identify item
 * kinds this module owns by inspecting an {@link ItemStack}'s NBT. Kept separate from the core class because the
 * core cannot see {@link CarKey} once gadget is a runtime module.
 */
public final class GadgetItemPredicates {

	public static final Predicate<ItemStack> CAR = stack -> hasTag(stack, CarKey.CAR_ID.getKey());

	public static final Predicate<ItemStack> JETPACK = stack -> hasTag(stack, JetpackKey.JETPACK_ID.getKey());

	private GadgetItemPredicates() {
	}

	private static boolean hasTag(ItemStack stack, String tag) {
		if (stack == null) {
			return false;
		}
		return new ItemBuilder(stack).hasNBTTag(tag);
	}
}
