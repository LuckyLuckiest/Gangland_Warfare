package me.luckyraven.market;

import me.luckyraven.file.configuration.Settings;
import me.luckyraven.market.contract.MarketMessageContract;
import me.luckyraven.market.ledger.TransactionRecord;
import me.luckyraven.market.snapshot.DailySnapshot;
import me.luckyraven.util.GanglandChatUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Default market message strings. Uses inline templates for now — future PRs can migrate these to the {@code Messages}
 * enum so server owners can localise them from {@code messages_xx.yml}.
 */
public final class GanglandMarketMessages implements MarketMessageContract {

	private static final String PREFIX = "&8[&6Market&8] ";

	@Override
	public String priceLine(String itemId, double currentPrice, double percentChange24h) {
		String arrow = percentChange24h > 0 ? "&a▲" : percentChange24h < 0 ? "&c▼" : "&7●";
		return GanglandChatUtil.color(PREFIX + "&f" + itemId + " &7= &e" + Settings.getMoneySymbol()
		                              + Settings.formatDouble(currentPrice) + "  " + arrow + " &f"
		                              + String.format("%+.2f%%", percentChange24h * 100D));
	}

	@Override
	public String historyHeader(String itemId, int days) {
		return GanglandChatUtil.color(PREFIX + "&fHistory for &e" + itemId + "&7 — last &e" + days + " &7day(s):");
	}

	@Override
	public String historyLine(DailySnapshot snapshot) {
		return GanglandChatUtil.color("&7  " + snapshot.snapshotDate() + "  &8| &fO: &e"
		                              + Settings.formatDouble(snapshot.open()) + "  &fH: &a"
		                              + Settings.formatDouble(snapshot.high()) + "  &fL: &c"
		                              + Settings.formatDouble(snapshot.low()) + "  &fC: &e"
		                              + Settings.formatDouble(snapshot.close()) + "  &fVol: &b" + snapshot.volume());
	}

	@Override
	public String trendLine(String itemId, double change24h, double change7d, double change30d) {
		return GanglandChatUtil.color(PREFIX + "&fTrend for &e" + itemId + "&7: 24h &e"
		                              + String.format("%+.2f%%", change24h * 100D) + " &7| 7d &e"
		                              + String.format("%+.2f%%", change7d * 100D) + " &7| 30d &e"
		                              + String.format("%+.2f%%", change30d * 100D));
	}

	@Override
	public String unknownItem(String itemId) {
		return GanglandChatUtil.color(PREFIX + "&cUnknown item: &f" + itemId);
	}

	@Override
	public String overrideSet(String itemId, double price) {
		return GanglandChatUtil.color(PREFIX + "&aOverride set for &f" + itemId + "&a = &e"
		                              + Settings.getMoneySymbol() + Settings.formatDouble(price));
	}

	@Override
	public String overrideCleared(String itemId) {
		return GanglandChatUtil.color(PREFIX + "&aOverride cleared for &f" + itemId);
	}

	@Override
	public String frozen(String itemId) {
		return GanglandChatUtil.color(PREFIX + "&bFrozen &f" + itemId);
	}

	@Override
	public String unfrozen(String itemId) {
		return GanglandChatUtil.color(PREFIX + "&bUnfrozen &f" + itemId);
	}

	@Override
	public String shockFired(String target, double multiplier, long durationMinutes) {
		return GanglandChatUtil.color(PREFIX + "&eShock fired &7on &f" + target + "&7: x&e"
		                              + Settings.formatDouble(multiplier) + " &7for &e" + durationMinutes + "m");
	}

	@Override
	public String ledgerHeader(int totalRows) {
		return GanglandChatUtil.color(PREFIX + "&fLedger &7— &e" + totalRows + " &7row(s)");
	}

	@Override
	public String ledgerRow(TransactionRecord record) {
		String dt  = LocalDateTime.ofInstant(record.timestamp(), ZoneId.systemDefault()).toLocalDate().toString();
		String tag = record.marketLinked() ? "&a●" : "&8●";
		return GanglandChatUtil.color("&7  " + dt + " " + tag + " &f" + record.direction() + " &7" + record.quantity()
		                              + "x &e" + record.itemId() + " &7@ &e" + Settings.getMoneySymbol()
		                              + Settings.formatDouble(record.unitPrice()));
	}

	@Override
	public String ledgerEmpty() {
		return GanglandChatUtil.color(PREFIX + "&7No transactions found.");
	}

	@Override
	public String marketDisabled() {
		return GanglandChatUtil.color(PREFIX + "&cThe market is disabled.");
	}
}
