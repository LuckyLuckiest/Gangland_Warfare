package me.luckyraven.market.ledger;

import me.luckyraven.market.contract.MarketLedgerRepositoryContract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Thin service around {@link MarketLedgerRepositoryContract}. Centralises transaction construction so callers never
 * build {@link TransactionRecord}s by hand.
 *
 * <p>Thread-safe: all delegates are HikariCP-backed and safe to invoke from
 * the async price ticker or from main-thread listeners.
 */
public final class TransactionLedger {

	private final MarketLedgerRepositoryContract repository;

	public TransactionLedger(MarketLedgerRepositoryContract repository) {
		this.repository = repository;
	}

	public TransactionRecord append(TransactionContext ctx) {
		TransactionRecord record = new TransactionRecord(
				UUID.randomUUID(),
				ctx.playerId(),
				ctx.traderId(),
				ctx.itemId(),
				ctx.quantity(),
				ctx.unitPrice(),
				ctx.total(),
				ctx.direction(),
				ctx.marketLinked(),
				Instant.now()
		);
		repository.append(record);
		return record;
	}

	public void appendRaw(TransactionRecord record) {
		repository.append(record);
	}

	public List<TransactionRecord> query(LedgerQuery query) {
		return repository.query(query);
	}

	public List<TransactionRecord> recentForPricing(String itemId, Instant since) {
		return repository.recentForPricing(itemId, since);
	}
}
