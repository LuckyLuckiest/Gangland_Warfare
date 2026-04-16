package me.luckyraven.shop.transaction;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Typed result of a {@link ShopBarterService#barter} invocation. {@link #delivery()} and {@link #consumed()} are only
 * populated on {@link BarterOutcome#SUCCESS}. {@link #askingValue()} and {@link #offeredValue()} reflect the targeting
 * threshold and the player's offered total at the moment the swap was attempted.
 */
public record BarterResult(BarterOutcome outcome,
                           @Nullable ItemStack delivery,
                           List<ItemStack> consumed,
                           double askingValue,
                           double offeredValue) {

	public BarterResult {
		consumed = consumed == null ? Collections.emptyList() : List.copyOf(consumed);
	}

	public static BarterResult success(ItemStack delivery, List<ItemStack> consumed,
	                                   double askingValue, double offeredValue) {
		return new BarterResult(BarterOutcome.SUCCESS, delivery, consumed, askingValue, offeredValue);
	}

	public static BarterResult of(BarterOutcome outcome, double askingValue, double offeredValue) {
		return new BarterResult(outcome, null, Collections.emptyList(), askingValue, offeredValue);
	}

	public static BarterResult of(BarterOutcome outcome) {
		return new BarterResult(outcome, null, Collections.emptyList(), 0.0, 0.0);
	}

}
