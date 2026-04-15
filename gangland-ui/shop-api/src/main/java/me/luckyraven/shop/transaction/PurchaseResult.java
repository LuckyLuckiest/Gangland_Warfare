package me.luckyraven.shop.transaction;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Typed result of a {@link ShopPurchaseService#purchase} invocation. Integrations switch on {@link #outcome()} and
 * resolve each case to their preferred Messages key. {@link #delivery()} and {@link #pricePaid()} are only populated on
 * {@link PurchaseOutcome#SUCCESS}; {@link #errorDetail()} carries an optional raw-string detail for economy failures
 * (e.g. the underlying {@code EconomyException} message) when it helps diagnose the failure.
 */
public record PurchaseResult(PurchaseOutcome outcome,
                             @Nullable ItemStack delivery,
                             double pricePaid,
                             @Nullable String errorDetail) {

	public static PurchaseResult success(ItemStack delivery, double pricePaid) {
		return new PurchaseResult(PurchaseOutcome.SUCCESS, delivery, pricePaid, null);
	}

	public static PurchaseResult of(PurchaseOutcome outcome) {
		return new PurchaseResult(outcome, null, 0D, null);
	}

	public static PurchaseResult economyError(String detail) {
		return new PurchaseResult(PurchaseOutcome.ECONOMY_ERROR, null, 0D, detail);
	}

}
