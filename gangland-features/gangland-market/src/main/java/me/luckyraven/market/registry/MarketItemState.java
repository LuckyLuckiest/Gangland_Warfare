package me.luckyraven.market.registry;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
public final class MarketItemState {

	private final String  itemId;
	private       double  currentPrice;
	private       double  basePrice;
	private       double  volatility;
	private       double  elasticity;
	private       boolean frozen;

	@Nullable
	private Double overridePrice;

	private long lastUpdatedMillis;

	public MarketItemState(String itemId, double basePrice, double volatility, double elasticity) {
		this.itemId            = itemId;
		this.basePrice         = basePrice;
		this.currentPrice      = basePrice;
		this.volatility        = volatility;
		this.elasticity        = elasticity;
		this.frozen            = false;
		this.overridePrice     = null;
		this.lastUpdatedMillis = System.currentTimeMillis();
	}

	public double effectivePrice() {
		return overridePrice != null ? overridePrice : currentPrice;
	}

	public boolean isOverridden() {
		return overridePrice != null;
	}
}
