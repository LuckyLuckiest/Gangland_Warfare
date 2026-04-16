package me.luckyraven.market.event;

public record MarketShock(
		String shockId,
		ShockTarget target,
		double multiplier,
		long durationMillis,
		long startedAtMillis
) {

	public long expiresAtMillis() {
		return startedAtMillis + durationMillis;
	}

	public boolean isActiveAt(long nowMillis) {
		return nowMillis >= startedAtMillis && nowMillis < expiresAtMillis();
	}
}
