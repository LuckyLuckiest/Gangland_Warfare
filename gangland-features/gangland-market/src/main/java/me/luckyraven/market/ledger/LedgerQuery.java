package me.luckyraven.market.ledger;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record LedgerQuery(
		@Nullable UUID playerId,
		@Nullable String itemId,
		@Nullable Instant from,
		@Nullable Instant to,
		int limit,
		int offset,
		boolean includeBlackMarket
) {

	public static LedgerQuery recent(int limit) {
		return new LedgerQuery(null, null, null, null, limit, 0, true);
	}

	public static LedgerQuery forPlayer(UUID playerId, int limit) {
		return new LedgerQuery(playerId, null, null, null, limit, 0, true);
	}

	public static LedgerQuery forItem(String itemId, int limit) {
		return new LedgerQuery(null, itemId, null, null, limit, 0, true);
	}
}
