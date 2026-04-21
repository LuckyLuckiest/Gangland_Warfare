package me.luckyraven.shop.transaction;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
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
                           BigDecimal askingValue,
                           BigDecimal offeredValue) {

	public BarterResult {
		consumed = consumed == null ? Collections.emptyList() : List.copyOf(consumed);
	}

	public static BarterResult success(ItemStack delivery, List<ItemStack> consumed,
	                                   BigDecimal askingValue, BigDecimal offeredValue) {
		return new BarterResult(BarterOutcome.SUCCESS, delivery, consumed, askingValue, offeredValue);
	}

	public static BarterResult of(BarterOutcome outcome, BigDecimal askingValue, BigDecimal offeredValue) {
		return new BarterResult(outcome, null, Collections.emptyList(), askingValue, offeredValue);
	}

	public static BarterResult of(BarterOutcome outcome) {
		return new BarterResult(outcome, null, Collections.emptyList(), BigDecimal.ZERO, BigDecimal.ZERO);
	}

}
