package org.luckyraven.gangland.copsncrooks.database;

import org.luckyraven.gangland.copsncrooks.bag.BagEntry;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/**
 * {@code hot_cash_bag}: one row per player who ever held a bag; zeroed rows stay so a spill is not undone by a restart.
 *
 * @since 0.20.0
 */
public class HotCashBagTable extends Table<BagEntry> {

	public HotCashBagTable() {
		super("hot_cash_bag");
		this.addAttribute(new Attribute<>("player_uuid", true, String.class));
		this.addAttribute(new Attribute<>("amount", false, Double.class));
		this.addAttribute(new Attribute<>("chase_taken", false, Double.class));
		this.addAttribute(new Attribute<>("banked_day", false, Long.class));
		this.addAttribute(new Attribute<>("banked_today", false, Double.class));
		this.addAttribute(new Attribute<>("seized", false, Double.class));
	}

	@Override
	public Object[] getData(BagEntry data) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public Map<String, Object> searchCriteria(BagEntry data) {
		return createSearchCriteria("player_uuid = ?", new Object[]{data.player().toString()},
		                            new int[]{Types.VARCHAR}, new int[]{0});
	}
}
