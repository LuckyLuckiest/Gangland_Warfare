package me.luckyraven.market.price;

import java.util.Collection;

public record PriceChangeBatch(Collection<PriceChange> changes, long tickTimestamp) {

	public boolean isEmpty() {
		return changes.isEmpty();
	}

	public int size() {
		return changes.size();
	}
}
