package org.luckyraven.gangland.core.money;

import lombok.CustomLog;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.keystone.datastructure.ScientificCalculator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Evaluates an admin-authored money formula without ever throwing: a broken or nonsensical formula yields the
 * caller's fallback, never below zero, with one console warning per distinct formula text.
 */
@CustomLog
public final class MoneyFormula {

	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	// test seam
	static Consumer<String> warnSink = log::warn;

	private MoneyFormula() {
	}

	public static double evaluate(String formula, Map<String, Double> variables, double fallback) {
		double safeFallback = clamp(fallback);
		String why;

		try {
			double result = new ScientificCalculator(formula, variables).evaluate();

			if (Double.isNaN(result)) {
				why = "the result is not a number";
			} else if (Double.isInfinite(result)) {
				why = "the result is infinite";
			} else if (result < 0) {
				why = "the result is negative";
			} else {
				return result;
			}
		} catch (RuntimeException e) {
			why = String.valueOf(e.getMessage());
		}

		if (WARNED.add(String.valueOf(formula))) {
			warnSink.accept("Money formula '" + formula + "' failed: " + why + "; using " + safeFallback);
		}
		return safeFallback;
	}

	public static Map<String, Double> userVariables(User<?> user) {
		Map<String, Double> variables = new HashMap<>();

		variables.put("balance", user.getEconomy().getAmount().doubleValue());
		variables.put("level", (double) user.getLevel().getLevelValue());
		variables.put("experience", user.getLevel().getExperience());
		variables.put("bounty", user.getBounty().getAmount().doubleValue());
		variables.put("wanted", (double) user.getWanted().getLevel());

		return variables;
	}

	// test seam
	static void resetWarnings() {
		WARNED.clear();
	}

	private static double clamp(double value) {
		return Double.isNaN(value) || Double.isInfinite(value) || value < 0 ? 0 : value;
	}

}
