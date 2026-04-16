package me.luckyraven.database.tables.market;

import me.luckyraven.market.ledger.TransactionRecord;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class MarketLedgerTable extends Table<TransactionRecord> {

	public MarketLedgerTable() {
		super("market_ledger");

		Attribute<String>  txId         = new Attribute<>("tx_id", true, String.class);
		Attribute<String>  playerUuid   = new Attribute<>("player_uuid", false, String.class);
		Attribute<String>  traderUuid   = new Attribute<>("trader_uuid", false, String.class);
		Attribute<String>  itemId       = new Attribute<>("item_id", false, String.class);
		Attribute<Integer> quantity     = new Attribute<>("quantity", false, Integer.class);
		Attribute<Double>  unitPrice    = new Attribute<>("unit_price", false, Double.class);
		Attribute<Double>  total        = new Attribute<>("total", false, Double.class);
		Attribute<String>  direction    = new Attribute<>("direction", false, String.class);
		Attribute<Boolean> marketLinked = new Attribute<>("market_linked", false, Boolean.class);
		Attribute<Long>    timestamp    = new Attribute<>("timestamp", false, Long.class);

		traderUuid.setDefaultValue("");

		this.addAttribute(txId);
		this.addAttribute(playerUuid);
		this.addAttribute(traderUuid);
		this.addAttribute(itemId);
		this.addAttribute(quantity);
		this.addAttribute(unitPrice);
		this.addAttribute(total);
		this.addAttribute(direction);
		this.addAttribute(marketLinked);
		this.addAttribute(timestamp);
	}

	@Override
	public Object[] getData(TransactionRecord data) {
		return new Object[]{
				data.txId().toString(),
				data.playerId().toString(),
				data.traderId() == null ? "" : data.traderId().toString(),
				data.itemId(),
				data.quantity(),
				data.unitPrice(),
				data.total(),
				data.direction().name(),
				data.marketLinked(),
				data.timestamp().toEpochMilli()
		};
	}

	@Override
	public Map<String, Object> searchCriteria(TransactionRecord data) {
		return createSearchCriteria("tx_id = ?", new Object[]{data.txId().toString()}, new int[]{Types.VARCHAR},
		                            new int[]{0});
	}
}
