package me.luckyraven.database.tables.market;

import me.luckyraven.market.registry.MarketItemState;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class MarketItemStateTable extends Table<MarketItemState> {

	public MarketItemStateTable() {
		super("market_item_state");

		Attribute<String>  itemId        = new Attribute<>("item_id", true, String.class);
		Attribute<Double>  currentPrice  = new Attribute<>("current_price", false, Double.class);
		Attribute<Double>  basePrice     = new Attribute<>("base_price", false, Double.class);
		Attribute<Double>  volatility    = new Attribute<>("volatility", false, Double.class);
		Attribute<Double>  elasticity    = new Attribute<>("elasticity", false, Double.class);
		Attribute<Boolean> frozen        = new Attribute<>("frozen", false, Boolean.class);
		Attribute<Boolean> isOverridden  = new Attribute<>("is_overridden", false, Boolean.class);
		Attribute<Double>  overridePrice = new Attribute<>("override_price", false, Double.class);
		Attribute<Long>    lastUpdated   = new Attribute<>("last_updated", false, Long.class);

		frozen.setDefaultValue(false);
		isOverridden.setDefaultValue(false);
		overridePrice.setDefaultValue(0D);

		this.addAttribute(itemId);
		this.addAttribute(currentPrice);
		this.addAttribute(basePrice);
		this.addAttribute(volatility);
		this.addAttribute(elasticity);
		this.addAttribute(frozen);
		this.addAttribute(isOverridden);
		this.addAttribute(overridePrice);
		this.addAttribute(lastUpdated);
	}

	@Override
	public Object[] getData(MarketItemState data) {
		boolean overridden = data.isOverridden();
		double  override   = overridden ? data.getOverridePrice() : 0D;

		return new Object[]{
				data.getItemId(),
				data.getCurrentPrice(),
				data.getBasePrice(),
				data.getVolatility(),
				data.getElasticity(),
				data.isFrozen(),
				overridden,
				override,
				data.getLastUpdatedMillis()
		};
	}

	@Override
	public Map<String, Object> searchCriteria(MarketItemState data) {
		return createSearchCriteria("item_id = ?", new Object[]{data.getItemId()}, new int[]{Types.VARCHAR},
		                            new int[]{0});
	}
}
