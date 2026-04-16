package me.luckyraven.market.price;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-tick gaussian noise bounded by the item's volatility. Injects small random movement so prices still breathe when
 * there are no transactions.
 */
public final class RandomWalkFactor {

	public double compute(double volatility) {
		if (volatility <= 0D) {
			return 1D;
		}
		return 1D + ThreadLocalRandom.current().nextGaussian() * volatility;
	}
}
