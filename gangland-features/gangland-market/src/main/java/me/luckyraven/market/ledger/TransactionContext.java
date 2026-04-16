package me.luckyraven.market.ledger;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Request struct handed to {@link me.luckyraven.market.bank.EconomyService} when charging or crediting. Pure data —
 * contains everything needed to construct a {@link TransactionRecord} once the balance mutation succeeds.
 */
public record TransactionContext(
		UUID playerId,
		@Nullable UUID traderId,
		String itemId,
		int quantity,
		double unitPrice,
		TransactionDirection direction,
		boolean marketLinked
) {

	public double total() {
		return unitPrice * quantity;
	}
}
