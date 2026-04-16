package me.luckyraven.market.price;

/**
 * Pulls the current price toward the configured base price at a fixed rate per tick. Stops the market from drifting
 * forever — keeps long-term prices tethered to the catalog value.
 */
public final class MeanReversionFactor {

	public double blend(double currentPrice, double basePrice, double rate) {
		return currentPrice + (basePrice - currentPrice) * rate;
	}
}
