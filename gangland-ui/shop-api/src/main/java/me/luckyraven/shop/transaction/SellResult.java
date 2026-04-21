package me.luckyraven.shop.transaction;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

public record SellResult(SellOutcome outcome, BigDecimal totalPaid, int itemsSold, @Nullable String errorDetail) {

	public static SellResult success(BigDecimal totalPaid, int itemsSold) {
		return new SellResult(SellOutcome.SUCCESS, totalPaid, itemsSold, null);
	}

	public static SellResult of(SellOutcome outcome) {
		return new SellResult(outcome, BigDecimal.ZERO, 0, null);
	}

	public static SellResult economyError(String detail) {
		return new SellResult(SellOutcome.ECONOMY_ERROR, BigDecimal.ZERO, 0, detail);
	}

}
