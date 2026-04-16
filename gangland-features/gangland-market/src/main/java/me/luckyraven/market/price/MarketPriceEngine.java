package me.luckyraven.market.price;

import lombok.CustomLog;
import me.luckyraven.market.contract.MarketSettingsContract;
import me.luckyraven.market.event.MarketShockRegistry;
import me.luckyraven.market.ledger.TransactionDirection;
import me.luckyraven.market.ledger.TransactionLedger;
import me.luckyraven.market.ledger.TransactionRecord;
import me.luckyraven.market.registry.MarketItemRegistry;
import me.luckyraven.market.registry.MarketItemState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Async-safe per-tick price recomputation. Reads the last N days of ledger rows (market-linked only), asks
 * {@link PriceModel} for a new price per item, persists the mutated state back to the repository, and batches the
 * resulting {@link PriceChange}s for dispatch on the main thread.
 *
 * <p><b>Threading:</b> {@link #tick()} is called from an async {@code RepeatingTimer}. It MUST NOT invoke any
 * Bukkit API — the dispatcher handles that. Reads the registry (ConcurrentHashMap-backed) and HikariCP repos only.
 */
@CustomLog
public final class MarketPriceEngine {

	private final MarketItemRegistry     registry;
	private final TransactionLedger      ledger;
	private final PriceModel             priceModel;
	private final PriceChangeDispatcher  dispatcher;
	private final MarketShockRegistry    shockRegistry;
	private final MarketSettingsContract settings;
	private final CategoryResolver       categoryResolver;

	public MarketPriceEngine(MarketItemRegistry registry,
	                         TransactionLedger ledger,
	                         PriceModel priceModel,
	                         PriceChangeDispatcher dispatcher,
	                         MarketShockRegistry shockRegistry,
	                         MarketSettingsContract settings,
	                         CategoryResolver categoryResolver) {
		this.registry         = registry;
		this.ledger           = ledger;
		this.priceModel       = priceModel;
		this.dispatcher       = dispatcher;
		this.shockRegistry    = shockRegistry;
		this.settings         = settings;
		this.categoryResolver = categoryResolver;
	}

	private static double sumVolume(Collection<TransactionRecord> records, TransactionDirection direction) {
		double sum = 0D;
		for (TransactionRecord record : records) {
			if (record.direction() == direction) {
				sum += record.quantity();
			}
		}
		return sum;
	}

	public void tick() {
		try {
			shockRegistry.pruneExpired();

			long now = System.currentTimeMillis();
			Instant windowStart = Instant.ofEpochMilli(
					now - TimeUnit.DAYS.toMillis(settings.getHistoryRetentionDays()));

			List<PriceChange> changes = new ArrayList<>();

			for (MarketItemState state : registry.all()) {
				List<TransactionRecord> recent     = ledger.recentForPricing(state.getItemId(), windowStart);
				double                  buyVolume  = sumVolume(recent, TransactionDirection.BUY);
				double                  sellVolume = sumVolume(recent, TransactionDirection.SELL);

				double previous = state.effectivePrice();
				double computed = priceModel.compute(state, buyVolume, sellVolume,
				                                     categoryResolver.resolve(state.getItemId()),
				                                     settings.getReversionRate(),
				                                     settings.getMinFloorMultiplier(),
				                                     settings.getMaxCeilingMultiplier());

				if (computed == previous) {
					continue;
				}

				state.setCurrentPrice(computed);
				state.setLastUpdatedMillis(now);
				registry.persist(state);
				changes.add(new PriceChange(state.getItemId(), previous, computed));
			}

			if (!changes.isEmpty()) {
				dispatcher.dispatch(new PriceChangeBatch(changes, now));
			}
		} catch (Exception e) {
			log.warn("MarketPriceEngine tick failed: {}", e.getMessage());
		}
	}
}
