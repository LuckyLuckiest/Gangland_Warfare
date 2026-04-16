package me.luckyraven.market.price;

/**
 * Hard floor/ceiling clamp relative to the base price.
 */
public final class PriceBounds {

	public double clamp(double price, double basePrice, double minMultiplier, double maxMultiplier) {
		double floor   = basePrice * minMultiplier;
		double ceiling = basePrice * maxMultiplier;

		if (price < floor) {
			return floor;
		}
		return Math.min(price, ceiling);
	}
}
