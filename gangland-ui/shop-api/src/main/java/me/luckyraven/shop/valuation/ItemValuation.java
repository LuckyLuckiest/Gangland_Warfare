package me.luckyraven.shop.valuation;

import java.math.BigDecimal;

public record ItemValuation(BigDecimal unitPrice, Source source, String categoryId) {

	public static final ItemValuation UNKNOWN = new ItemValuation(BigDecimal.ZERO, Source.UNKNOWN, null);

	public enum Source {
		CATEGORY,
		UNKNOWN
	}

	public boolean hasValue() {
		return source != Source.UNKNOWN && unitPrice.signum() > 0;
	}

}
