package me.luckyraven.shop.transaction;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public record TradeInResult(TradeInOutcome outcome,
                            double moneyOwed,
                            double tradeInCredit,
                            @Nullable ItemStack delivery,
                            @Nullable String errorDetail) {

	public static TradeInResult success(double moneyOwed, double tradeInCredit, ItemStack delivery) {
		return new TradeInResult(TradeInOutcome.SUCCESS, moneyOwed, tradeInCredit, delivery, null);
	}

	public static TradeInResult of(TradeInOutcome outcome, double moneyOwed, double tradeInCredit) {
		return new TradeInResult(outcome, moneyOwed, tradeInCredit, null, null);
	}

	public static TradeInResult economyError(String detail) {
		return new TradeInResult(TradeInOutcome.ECONOMY_ERROR, 0D, 0D, null, detail);
	}

}
