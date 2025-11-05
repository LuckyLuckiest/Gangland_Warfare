package me.luckyraven;

public final class NumberUtil {

	private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "P", "E", "Z", "Y"};

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

	public static boolean isValueFormatted(String value) {
		return value.toUpperCase().matches(".*[KMBTPEZY]$");
	}

	public static double parseFormattedDouble(String input) {
		input = input.toUpperCase().trim();

		for (int i = SUFFIXES.length - 1; i >= 1; i--) {
			String suffix = SUFFIXES[i];

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

	public static int parseFormattedInteger(String input) {
		return (int) parseFormattedDouble(input);
	}

	/**
	 * <a href="https://en.wikipedia.org/wiki/Linear_interpolation">Linear interpolation</a>
	 * implementation. The returned number will be between min inclusively, and max inclusively.
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

}
