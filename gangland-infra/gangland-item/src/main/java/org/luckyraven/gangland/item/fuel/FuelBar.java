package org.luckyraven.gangland.item.fuel;

/**
 * Renders a fuel gauge string for action bar display. The bar uses a fixed number of segments colored green for filled
 * and gray for empty.
 *
 * <p>Example output: {@code "&6⛽ Fuel &a||||||||||&7|||||||||| &f3000&7/&f6000"}
 */
public final class FuelBar {

	private static final int  BAR_LENGTH = 20;
	private static final char SEGMENT    = '|';

	private FuelBar() {
	}

	/**
	 * Renders a fuel gauge string suitable for action bar display.
	 *
	 * @param currentFuel the current fuel amount
	 * @param maxFuel the maximum fuel capacity
	 *
	 * @return a color-coded fuel bar string
	 */
	public static String render(int currentFuel, int maxFuel) {
		// No separate maxFuel<=0 "Unlimited" branch: every write path clamps currentFuel<=maxFuel, so a
		// zero/negative capacity always implies currentFuel<=0 too - the branch below already covers it (gi=56).
		if (currentFuel <= 0) return "&6⛽ Fuel &cEmpty";

		int filled = (int) Math.round((double) currentFuel / maxFuel * BAR_LENGTH);
		filled = Math.max(0, Math.min(filled, BAR_LENGTH));
		int empty = BAR_LENGTH - filled;

		StringBuilder bar = new StringBuilder();
		bar.append("&6⛽ Fuel ");

		// Green filled portion
		bar.append("&a");
		bar.append(String.valueOf(SEGMENT).repeat(Math.max(0, filled)));

		// Gray empty portion
		bar.append("&7");
		bar.append(String.valueOf(SEGMENT).repeat(Math.max(0, empty)));

		bar.append(" &f").append(currentFuel).append("&7/&f").append(maxFuel);
		return bar.toString();
	}

}
