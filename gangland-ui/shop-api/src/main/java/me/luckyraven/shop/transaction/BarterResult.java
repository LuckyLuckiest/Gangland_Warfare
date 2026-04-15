package me.luckyraven.shop.transaction;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Typed result of a {@link ShopBarterService#barter} invocation. {@link #delivery()} and {@link #consumed()} are only
 * populated on {@link BarterOutcome#SUCCESS}.
 */
public record BarterResult(BarterOutcome outcome,
                           @Nullable ItemStack delivery,
                           @Nullable ItemStack consumed) {

	public static BarterResult success(ItemStack delivery, ItemStack consumed) {
		return new BarterResult(BarterOutcome.SUCCESS, delivery, consumed);
	}

	public static BarterResult of(BarterOutcome outcome) {
		return new BarterResult(outcome, null, null);
	}

}
