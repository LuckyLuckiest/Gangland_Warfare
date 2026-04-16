package me.luckyraven.market.registry;

import me.luckyraven.market.contract.MarketItemStateRepositoryContract;
import me.luckyraven.market.contract.MarketSettingsContract;
import me.luckyraven.market.contract.MarketSettingsContract.ItemOverride;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory authoritative map of {@link MarketItemState}. Safe to read from the async price ticker; writes go through
 * {@link #persist(MarketItemState)} which updates memory + delegates to the repository contract on the caller's thread
 * (async inside the tick, main during listener ingestion).
 */
public final class MarketItemRegistry {

	private final MarketItemStateRepositoryContract          repository;
	private final MarketSettingsContract                     settings;
	private final ConcurrentHashMap<String, MarketItemState> states = new ConcurrentHashMap<>();

	public MarketItemRegistry(MarketItemStateRepositoryContract repository,
	                          MarketSettingsContract settings) {
		this.repository = repository;
		this.settings   = settings;
	}

	/**
	 * Called once at startup after the repositories are online.
	 */
	public void hydrate() {
		states.clear();
		for (MarketItemState state : repository.loadAll()) {
			states.put(state.getItemId(), state);
		}
	}

	public Optional<MarketItemState> find(String itemId) {
		return Optional.ofNullable(states.get(itemId));
	}

	public MarketItemState ensureRegistered(String itemId, double seedBasePrice) {
		return states.computeIfAbsent(itemId, id -> {
			ItemOverride override = settings.getPerItemOverrides().get(id);
			double basePrice = override != null && override.basePrice() != null
			                   ? override.basePrice()
			                   : seedBasePrice;
			double elasticity = override != null && override.elasticity() != null
			                    ? override.elasticity()
			                    : settings.getElasticityDefault();
			double volatility = override != null && override.volatility() != null
			                    ? override.volatility()
			                    : settings.getVolatilityDefault();

			MarketItemState fresh = new MarketItemState(id, basePrice, volatility, elasticity);
			repository.upsert(fresh);
			return fresh;
		});
	}

	public void persist(MarketItemState state) {
		states.put(state.getItemId(), state);
		repository.upsert(state);
	}

	public Collection<MarketItemState> all() {
		return Collections.unmodifiableCollection(states.values());
	}

	public int size() {
		return states.size();
	}
}
