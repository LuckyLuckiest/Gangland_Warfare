package me.luckyraven.market.price;

import me.luckyraven.market.contract.MarketPriceContract;
import me.luckyraven.market.contract.MarketSettingsContract;
import me.luckyraven.market.contract.MarketSettingsContract.IndexWeighting;
import me.luckyraven.market.contract.MarketSnapshotRepositoryContract;
import me.luckyraven.market.registry.MarketItemRegistry;
import me.luckyraven.market.registry.MarketItemState;
import me.luckyraven.market.snapshot.DailySnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Default {@link MarketPriceContract} implementation backed by the registry + snapshot repo.
 */
public final class MarketPriceService implements MarketPriceContract {

	private final MarketItemRegistry               registry;
	private final MarketSnapshotRepositoryContract snapshotRepository;
	private final MarketSettingsContract           settings;

	public MarketPriceService(MarketItemRegistry registry,
	                          MarketSnapshotRepositoryContract snapshotRepository,
	                          MarketSettingsContract settings) {
		this.registry           = registry;
		this.snapshotRepository = snapshotRepository;
		this.settings           = settings;
	}

	@Override
	public double currentPrice(String itemId) {
		return registry.find(itemId).map(MarketItemState::effectivePrice).orElse(0D);
	}

	@Override
	public Optional<MarketItemState> find(String itemId) {
		return registry.find(itemId);
	}

	@Override
	public void ensureRegistered(String itemId, double seedBasePrice) {
		registry.ensureRegistered(itemId, seedBasePrice);
	}

	@Override
	public double percentageChange(String itemId, int days) {
		List<DailySnapshot> history = snapshotRepository.history(itemId, days);
		if (history.size() < 2) {
			return 0D;
		}
		DailySnapshot newest = history.getFirst();
		DailySnapshot oldest = history.getLast();
		if (oldest.close() == 0D) {
			return 0D;
		}
		return (newest.close() - oldest.close()) / oldest.close();
	}

	@Override
	public Collection<MarketItemState> allStates() {
		return registry.all();
	}

	@Override
	public double index() {
		Collection<MarketItemState> all = registry.all();
		if (all.isEmpty()) {
			return 1D;
		}

		IndexWeighting weighting = settings.getIndexWeighting();
		double         sum       = 0D;
		double         weights   = 0D;

		for (MarketItemState state : all) {
			if (state.getBasePrice() == 0D) {
				continue;
			}
			double ratio = state.effectivePrice() / state.getBasePrice();
			double weight = switch (weighting) {
				case EQUAL -> 1D;
				case VOLUME -> Math.max(1D, state.getBasePrice());
				case MARKET_CAP -> Math.max(1D, state.effectivePrice());
			};
			sum += ratio * weight;
			weights += weight;
		}

		return weights == 0D ? 1D : sum / weights;
	}
}
