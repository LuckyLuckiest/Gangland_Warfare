package me.luckyraven.market.price;

/**
 * Pushes price up when buy volume exceeds sell volume, down when the reverse is true. Clamped by elasticity so a single
 * high-volume day can't swing the price by more than {@code elasticity} of the previous tick.
 */
public final class SupplyDemandFactor {

	public double compute(double buyVolume, double sellVolume, double elasticity) {
		double total = buyVolume + sellVolume;
		if (total <= 0D) {
			return 1D;
		}
		return 1D + elasticity * (buyVolume - sellVolume) / total;
	}
}
