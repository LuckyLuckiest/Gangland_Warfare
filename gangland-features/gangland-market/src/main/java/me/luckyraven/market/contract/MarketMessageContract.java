package me.luckyraven.market.contract;

import me.luckyraven.market.ledger.TransactionRecord;
import me.luckyraven.market.snapshot.DailySnapshot;

public interface MarketMessageContract {

	String priceLine(String itemId, double currentPrice, double percentChange24h);

	String historyHeader(String itemId, int days);

	String historyLine(DailySnapshot snapshot);

	String trendLine(String itemId, double change24h, double change7d, double change30d);

	String unknownItem(String itemId);

	String overrideSet(String itemId, double price);

	String overrideCleared(String itemId);

	String frozen(String itemId);

	String unfrozen(String itemId);

	String shockFired(String target, double multiplier, long durationMinutes);

	String ledgerHeader(int totalRows);

	String ledgerRow(TransactionRecord record);

	String ledgerEmpty();

	String marketDisabled();
}
