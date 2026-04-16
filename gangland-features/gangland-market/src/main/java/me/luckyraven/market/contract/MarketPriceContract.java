package me.luckyraven.market.contract;

import me.luckyraven.market.registry.MarketItemState;

import java.util.Collection;
import java.util.Optional;

/**
 * Exposed to traders and other feature modules. Read-only snapshot of the live market. Never imports shop-api or trader
 * code.
 */
public interface MarketPriceContract {

	double currentPrice(String itemId);

	Optional<MarketItemState> find(String itemId);

	/**
	 * Register an item the first time it's encountered by a trader. Safe to call repeatedly — subsequent calls are
	 * no-ops if the item already exists.
	 */
	void ensureRegistered(String itemId, double seedBasePrice);

	/**
	 * Percentage change across the last N days (0..1). Returns 0 when history is shorter than the requested window.
	 */
	double percentageChange(String itemId, int days);

	Collection<MarketItemState> allStates();

	/**
	 * Weighted aggregate index across all tracked items, used by {@code %gangland_market_index%}.
	 */
	double index();
}
