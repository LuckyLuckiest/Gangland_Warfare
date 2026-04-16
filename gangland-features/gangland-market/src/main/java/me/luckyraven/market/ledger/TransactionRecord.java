package me.luckyraven.market.ledger;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record TransactionRecord(
		UUID txId,
		UUID playerId,
		@Nullable UUID traderId,
		String itemId,
		int quantity,
		double unitPrice,
		double total,
		TransactionDirection direction,
		boolean marketLinked,
		Instant timestamp
) {
}
