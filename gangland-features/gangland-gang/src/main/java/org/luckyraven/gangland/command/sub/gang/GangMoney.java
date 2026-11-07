package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.keystone.util.NumberUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.NavigableSet;

/** The arithmetic and the amount suggestions {@code /glw gang deposit} and {@code /glw gang withdraw} share. */
final class GangMoney {

	private GangMoney() {
	}

	/**
	 * The contribution points an amount is worth: the amount over the configured contribution rate, rounded to one
	 * decimal place for every digit the whole part of the rate has beyond the first.
	 */
	static double contribution(BigDecimal amount) {
		double rate   = GangSettings.getGangContributionRate();
		int    length = String.valueOf((int) rate).length() - 1;
		double round  = Math.pow(10, length);

		return Math.round(amount.doubleValue() / rate * round) / round;
	}

	/** Tab suggestions for an amount argument, up to {@code balance}: two decimals at most, whole numbers bare. */
	static List<String> amountSuggestions(double balance) {
		NavigableSet<Double> values = NumberUtil.getSetOfNumbers(balance);

		return values.stream()
				.map(value -> Double.parseDouble(value.toString()))
				.map(value -> Math.round(value * 100.0) / 100.0)
				.sorted()
				.map(value -> value % 1 == 0 ? String.valueOf(value.longValue()) : String.format("%.2f", value))
				.toList();
	}

}
