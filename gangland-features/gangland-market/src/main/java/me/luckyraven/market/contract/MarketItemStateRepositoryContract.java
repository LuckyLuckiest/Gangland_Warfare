package me.luckyraven.market.contract;

import me.luckyraven.market.registry.MarketItemState;

import java.util.Collection;
import java.util.Optional;

public interface MarketItemStateRepositoryContract {

	void upsert(MarketItemState state);

	Optional<MarketItemState> find(String itemId);

	Collection<MarketItemState> loadAll();
}
