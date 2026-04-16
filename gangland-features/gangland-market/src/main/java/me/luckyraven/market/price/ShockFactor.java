package me.luckyraven.market.price;

import me.luckyraven.market.event.MarketShock;
import me.luckyraven.market.event.MarketShockRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * Multiplies the price by every currently active shock that targets the item (or its category). Multiple overlapping
 * shocks compound.
 */
public final class ShockFactor {

	private final MarketShockRegistry registry;

	public ShockFactor(MarketShockRegistry registry) {
		this.registry = registry;
	}

	public double multiplierFor(String itemId, @Nullable String categoryId) {
		double result = 1D;
		for (MarketShock shock : registry.active()) {
			if (shock.target().matches(itemId, categoryId == null ? "" : categoryId)) {
				result *= shock.multiplier();
			}
		}
		return result;
	}
}
