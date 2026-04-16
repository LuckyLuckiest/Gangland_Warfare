package me.luckyraven.market.contract;

import java.util.Map;

public interface MarketSettingsContract {

	boolean isEnabled();

	int getTickIntervalMinutes();

	/**
	 * "HH:mm" 24-hour time in the server's local zone.
	 */
	String getSnapshotTime();

	int getHistoryRetentionDays();

	double getElasticityDefault();

	double getVolatilityDefault();

	double getReversionRate();

	double getMinFloorMultiplier();

	double getMaxCeilingMultiplier();

	IndexWeighting getIndexWeighting();

	Map<String, ItemOverride> getPerItemOverrides();

	enum IndexWeighting {
		EQUAL,
		VOLUME,
		MARKET_CAP
	}

	record ItemOverride(
			Double basePrice,
			Double elasticity,
			Double volatility
	) {
	}
}
