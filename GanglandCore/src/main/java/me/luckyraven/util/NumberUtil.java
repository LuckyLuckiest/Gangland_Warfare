package me.luckyraven.util;

public final class NumberUtil {

	private NumberUtil() { }

	/**
	 * <a href="https://en.wikipedia.org/wiki/Linear_interpolation">Linear interpolation</a>
	 * implementation. The returned number will be between min inclusively, and max inclusively.
	 *
	 * <p>The factor decides where, relative to <code>min</code> and
	 * <code>max</code>, the returned point will be. The factor should be a
	 * number between 0 inclusively and 1 inclusively. Values approaching 0.0 will return a point closer to
	 * <code>min</code>, while values approaching 1.0 will return a point closer to <code>max</code>.
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

	public static int intFloor(double value) {
		int i = (int) value;
		return value < (double) i ? i - 1 : i;
	}

	/**
	 * Returns 0 if value is 0, 1 if value is more than 0 and -1 if value is less than 0
	 *
	 * @param value the value which signum to return
	 *
	 * @return the value in signum
	 */
	public static int sign(double value) {
		if (value == 0.0) {
			return 0;
		} else {
			return value > 0.0 ? 1 : -1;
		}
	}

	public static long longFloor(double value) {
		long l = (long) value;
		return value < (double) l ? l - 1 : l;
	}

	public static double frac(double value) {
		return value - (double) NumberUtil.longFloor(value);
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
