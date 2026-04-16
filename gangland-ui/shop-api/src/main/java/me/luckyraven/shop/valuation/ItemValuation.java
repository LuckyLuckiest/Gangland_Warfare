package me.luckyraven.shop.valuation;

public record ItemValuation(double unitPrice, Source source, String categoryId) {

	public static final ItemValuation UNKNOWN = new ItemValuation(0.0, Source.UNKNOWN, null);

	public enum Source {
		CATEGORY,
		UNKNOWN
	}

	public boolean hasValue() {
		return source != Source.UNKNOWN && unitPrice > 0.0;
	}

}
