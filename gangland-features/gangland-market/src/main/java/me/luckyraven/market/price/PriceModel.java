package me.luckyraven.market.price;

import me.luckyraven.market.registry.MarketItemState;
import org.jetbrains.annotations.Nullable;

/**
 * Composes the four drivers into a single new-price computation. Pure math — no I/O, no Bukkit calls — so it is legal
 * to invoke off the main thread.
 */
public final class PriceModel {

	private final SupplyDemandFactor  supplyDemand;
	private final MeanReversionFactor reversion;
	private final RandomWalkFactor    randomWalk;
	private final ShockFactor         shock;
	private final PriceBounds         bounds;

	public PriceModel(SupplyDemandFactor supplyDemand,
	                  MeanReversionFactor reversion,
	                  RandomWalkFactor randomWalk,
	                  ShockFactor shock,
	                  PriceBounds bounds) {
		this.supplyDemand = supplyDemand;
		this.reversion    = reversion;
		this.randomWalk   = randomWalk;
		this.shock        = shock;
		this.bounds       = bounds;
	}

	public double compute(MarketItemState state,
	                      double buyVolume,
	                      double sellVolume,
	                      @Nullable String categoryId,
	                      double reversionRate,
	                      double minFloorMultiplier,
	                      double maxCeilingMultiplier) {
		// Admin overrides and freezes short-circuit the entire model.
		if (state.isOverridden()) {
			Double overridePrice = state.getOverridePrice();
			return overridePrice != null ? overridePrice : 0D;
		}
		if (state.isFrozen()) {
			return state.getCurrentPrice();
		}

		double previous = state.getCurrentPrice();
		double base     = state.getBasePrice();

		double sd     = supplyDemand.compute(buyVolume, sellVolume, state.getElasticity());
		double shockM = shock.multiplierFor(state.getItemId(), categoryId);
		double noise  = randomWalk.compute(state.getVolatility());

		double raw     = previous * sd * shockM * noise;
		double clamped = bounds.clamp(raw, base, minFloorMultiplier, maxCeilingMultiplier);

		return reversion.blend(clamped, base, reversionRate);
	}
}
