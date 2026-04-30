package org.luckyraven.gangland.core.utilities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public final class NumberUtil {

	/**
	 * Short-scale currency suffixes, step {@code 10^3} per entry. Extended beyond the old K-M-B-T-P-E-Z-Y table so
	 * Vault-tier balances beyond 10^24 still format cleanly — {@code Dc} (decillion, 10^33) and {@code Un}
	 * (undecillion, 10^36) are the common terminal points in incremental-game notation. Values above that fall back to
	 * the last suffix and keep scaling numerically.
	 */
	private static final String[] SUFFIXES = {
			"",   // 10^0
			"K",  // 10^3   thousand
			"M",  // 10^6   million
			"B",  // 10^9   billion
			"T",  // 10^12  trillion
			"P",  // 10^15  quadrillion (peta)
			"E",  // 10^18  quintillion (exa)
			"Z",  // 10^21  sextillion  (zetta)
			"Y",  // 10^24  septillion  (yotta)
			"R",  // 10^27  octillion   (ronna)
			"Q",  // 10^30  nonillion   (quetta)
			"Dc", // 10^33  decillion
			"Un", // 10^36  undecillion
			"Du", // 10^39  duodecillion
			"Td", // 10^42  tredecillion
			"Qt", // 10^45  quattuordecillion
			"Qd", // 10^48  quindecillion
			"Sd", // 10^51  sexdecillion
			"St", // 10^54  septendecillion
			"Od", // 10^57  octodecillion
			"Nd", // 10^60  novemdecillion
			"Vg"  // 10^63  vigintillion
	};

	private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

	public static String formatDouble(String format, double value) {
		return String.format(format, value).replaceAll("\\.?0*$", "");
	}

	public static String valueFormat(double value) {
		return valueFormat("%,.2f", value);
	}

	public static String valueFormat(String format, double value) {
		int    index    = 0;
		double modValue = value;

		while (modValue >= 1_000 && index < SUFFIXES.length - 1) {
			modValue /= 1_000;
			++index;
		}

		return formatDouble(format, modValue) + SUFFIXES[index];
	}

	/**
	 * {@link BigDecimal} overload. Uses BigDecimal arithmetic so values beyond {@code 2^53} scale down without
	 * {@code double} precision loss before formatting. Once under 1000, the mantissa is projected to {@code double} for
	 * the final {@code String.format} pass — at that point the value is in the {@code [0, 999]} range where
	 * {@code double} is exact.
	 */
	public static String valueFormat(BigDecimal value) {
		return valueFormat("%,.2f", value);
	}

	public static String valueFormat(String format, BigDecimal value) {
		if (value == null) return formatDouble(format, 0D) + SUFFIXES[0];

		BigDecimal modValue = value;
		boolean    negative = modValue.signum() < 0;
		if (negative) modValue = modValue.negate();

		int index = 0;
		while (modValue.compareTo(THOUSAND) >= 0 && index < SUFFIXES.length - 1) {
			modValue = modValue.divide(THOUSAND, 4, RoundingMode.HALF_UP);
			++index;
		}

		String rendered = formatDouble(format, modValue.doubleValue()) + SUFFIXES[index];
		return negative ? "-" + rendered : rendered;
	}

	public static NavigableSet<Double> getSetOfNumbers(double balance) {
		NavigableSet<Double> values = new TreeSet<>((a, b) -> Double.compare(b, a));
		values.add(balance);

		double[] steps = {1.0D, 0.75D, 0.5D, 0.25D, 0.1D};
		double   scale = Math.pow(10, Math.floor(Math.log10(balance)));

		double roundedTop = Math.floor(balance / scale) * scale;
		if (roundedTop > 0D) values.add(roundedTop);

		while (scale >= 1D) {
			for (double step : steps) {
				double value = scale * step;

				if (value <= balance) {
					values.add(value);
				}
			}

			scale /= 10D;
		}
		return values;
	}

	public static boolean isValueFormatted(String value) {
		String upper = value.toUpperCase();
		for (int i = SUFFIXES.length - 1; i >= 1; i--) {
			if (upper.endsWith(SUFFIXES[i].toUpperCase())) return true;
		}
		return false;
	}

	public static double parseFormattedDouble(String input) {
		input = input.toUpperCase().trim();

		for (int i = SUFFIXES.length - 1; i >= 1; i--) {
			String suffix = SUFFIXES[i].toUpperCase();

			if (!input.endsWith(suffix)) continue;

			String numberPart = input.substring(0, input.length() - suffix.length());

			try {
				return Double.parseDouble(numberPart) * Math.pow(1000, i);
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException("Invalid formatted number: " + input);
			}
		}

		try {
			return Double.parseDouble(input);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Invalid number: " + input);
		}
	}

	/**
	 * {@link BigDecimal} parse counterpart to {@link #parseFormattedDouble(String)}. Supports suffixes up to
	 * {@code 10^63} without precision loss.
	 */
	public static BigDecimal parseFormattedBigDecimal(String input) {
		String upper = input.toUpperCase().trim();

		for (int i = SUFFIXES.length - 1; i >= 1; i--) {
			String suffix = SUFFIXES[i].toUpperCase();

			if (!upper.endsWith(suffix)) continue;

			String numberPart = upper.substring(0, upper.length() - suffix.length()).trim();

			try {
				BigDecimal mantissa   = new BigDecimal(numberPart);
				BigDecimal multiplier = THOUSAND.pow(i);
				return mantissa.multiply(multiplier);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid formatted number: " + input);
			}
		}

		try {
			return new BigDecimal(upper);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid number: " + input);
		}
	}

	public static int parseFormattedInteger(String input) {
		return (int) parseFormattedDouble(input);
	}

	/**
	 * <a href="https://en.wikipedia.org/wiki/Linear_interpolation">Linear interpolation</a>
	 * implementation. The returned number will be between min inclusively and max inclusively.
	 *
	 * <p>The factor decides where, relative to <code>min</code> and <code>max</code>, the returned point will be. The
	 * factor should be a number between 0 inclusively and 1 inclusively. Values approaching 0.0 will return a point
	 * closer to <code>min</code>, while values approaching 1.0 will return a point closer to <code>max</code>.
	 *
	 * @param min The minimum value that the function can return.
	 * @param max The maximum value that the function can return.
	 * @param factor At what point between the minimum and maximum should the returned value be.
	 *
	 * @return The interpolated number.
	 */
	public static double lerp(double min, double max, double factor) {
		return min + factor * (max - min);
	}

	/**
	 * Shorthand to square a given number. Avoids using <code>Math.pow</code>, which wastes resources.
	 *
	 * @param num The number to square.
	 *
	 * @return The result.
	 */
	public static double square(double num) {
		return num * num;
	}

	/**
	 * Increases the size of the original list by linear interpolation.
	 *
	 * @param original the original list.
	 * @param targetSize the target size of the new list.
	 *
	 * @return the new resized linearly interpolated list.
	 */
	public static List<Integer> resizeLinear(List<Integer> original, int targetSize) {
		if (targetSize <= 0) {
			throw new IllegalArgumentException("Target size must be > 0");
		}

		List<Integer> result = new ArrayList<>(targetSize);

		// Case 1: Only one number → increment by itself
		if (original.size() == 1) {
			int step = original.getFirst();

			for (int i = 1; i <= targetSize; i++) {
				result.add(step * i);
			}

			return result;
		}

		int originalSize = original.size();

		for (int i = 0; i < targetSize; i++) {
			// Map new index to old index range
			double position   = (double) i * (originalSize - 1) / (targetSize - 1);
			int    leftIndex  = (int) Math.floor(position);
			int    rightIndex = Math.min(leftIndex + 1, originalSize - 1);

			double fraction = position - leftIndex;

			int leftValue  = original.get(leftIndex);
			int rightValue = original.get(rightIndex);

			// Linear interpolation
			int value = (int) Math.round(leftValue + fraction * (rightValue - leftValue));

			result.add(value);
		}

		return result;
	}

}
