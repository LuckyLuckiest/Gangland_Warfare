package me.luckyraven.market.contract;

import me.luckyraven.market.ledger.LedgerQuery;
import me.luckyraven.market.ledger.TransactionRecord;

import java.time.Instant;
import java.util.List;

/**
 * Persistence contract for {@link TransactionRecord}s. Implemented by a repository in {@code gangland-impl} — the
 * market module never sees {@code AbstractRepository}.
 */
public interface MarketLedgerRepositoryContract {

	void append(TransactionRecord record);

	List<TransactionRecord> query(LedgerQuery query);

	/**
	 * Records used by the price engine. Only market-linked rows (black-market trades are skipped) within the given
	 * lookback window.
	 */
	List<TransactionRecord> recentForPricing(String itemId, Instant since);
}
