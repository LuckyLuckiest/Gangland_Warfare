package me.luckyraven.market.snapshot;

import lombok.CustomLog;
import me.luckyraven.market.contract.MarketSettingsContract;
import me.luckyraven.market.contract.MarketSnapshotRepositoryContract;
import me.luckyraven.market.ledger.TransactionLedger;
import me.luckyraven.market.ledger.TransactionRecord;
import me.luckyraven.market.registry.MarketItemRegistry;
import me.luckyraven.market.registry.MarketItemState;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Runs once per day on an async timer. Writes one {@link DailySnapshot} per tracked item and prunes rows older than
 * {@code History_Retention_Days}. Approximates intraday OHLC from the last-known-price + same-day ledger volume —
 * sufficient for the 30-day player-facing chart.
 */
@CustomLog
public final class SnapshotService {

	private final MarketItemRegistry               registry;
	private final TransactionLedger                ledger;
	private final MarketSnapshotRepositoryContract repository;
	private final MarketSettingsContract           settings;

	public SnapshotService(MarketItemRegistry registry,
	                       TransactionLedger ledger,
	                       MarketSnapshotRepositoryContract repository,
	                       MarketSettingsContract settings) {
		this.registry   = registry;
		this.ledger     = ledger;
		this.repository = repository;
		this.settings   = settings;
	}

	/**
	 * Async-safe: pure I/O + math, no Bukkit calls.
	 */
	public void snapshot() {
		try {
			LocalDate today = LocalDate.now(ZoneId.systemDefault());
			Instant   start = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
			Instant   now   = Instant.now();

			for (MarketItemState state : registry.all()) {
				double price = state.effectivePrice();

				List<TransactionRecord> daily  = ledger.recentForPricing(state.getItemId(), start);
				long                    volume = daily.stream().mapToLong(TransactionRecord::quantity).sum();

				double previousClose = previousClose(state.getItemId(), price);
				double high          = Math.max(previousClose, price);
				double low           = Math.min(previousClose, price);

				DailySnapshot snapshot = new DailySnapshot(state.getItemId(), today, previousClose, high, low, price,
				                                           volume);
				repository.save(snapshot);
			}

			int cutoffDays = settings.getHistoryRetentionDays();
			repository.pruneOlderThan(today.minusDays(cutoffDays));
			log.info("Market snapshot run complete: %d items, pruned older than %d days".formatted(registry.size(),
			                                                                                       cutoffDays));
		} catch (Exception e) {
			log.warn("SnapshotService run failed: " + e.getMessage());
		}
	}

	private double previousClose(String itemId, double fallback) {
		List<DailySnapshot> recent = repository.history(itemId, 1);
		return recent.isEmpty() ? fallback : recent.get(0).close();
	}
}
