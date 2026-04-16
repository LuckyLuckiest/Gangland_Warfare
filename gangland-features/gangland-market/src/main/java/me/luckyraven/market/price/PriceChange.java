package me.luckyraven.market.price;

public record PriceChange(String itemId, double previousPrice, double newPrice) {

	public double delta() {
		return newPrice - previousPrice;
	}

	public double percent() {
		if (previousPrice == 0D) {
			return 0D;
		}
		return (newPrice - previousPrice) / previousPrice;
	}
}
