package me.luckyraven.shop.transaction;

import org.jetbrains.annotations.Nullable;

public record SellResult(SellOutcome outcome, double totalPaid, int itemsSold, @Nullable String errorDetail) {

	public static SellResult success(double totalPaid, int itemsSold) {
		return new SellResult(SellOutcome.SUCCESS, totalPaid, itemsSold, null);
	}

	public static SellResult of(SellOutcome outcome) {
		return new SellResult(outcome, 0D, 0, null);
	}

	public static SellResult economyError(String detail) {
		return new SellResult(SellOutcome.ECONOMY_ERROR, 0D, 0, detail);
	}

}
