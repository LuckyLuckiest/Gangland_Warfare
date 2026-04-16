package me.luckyraven.market;

import me.luckyraven.file.configuration.Settings;
import me.luckyraven.market.contract.MarketSettingsContract;

import java.util.Map;

/**
 * {@link MarketSettingsContract} backed by the static {@link Settings} loader.
 */
public final class GanglandMarketSettings implements MarketSettingsContract {

	@Override
	public boolean isEnabled() {
		return Settings.isMarketEnabled();
	}

	@Override
	public int getTickIntervalMinutes() {
		return Settings.getMarketTickIntervalMinutes();
	}

	@Override
	public String getSnapshotTime() {
		return Settings.getMarketSnapshotTime();
	}

	@Override
	public int getHistoryRetentionDays() {
		return Settings.getMarketHistoryRetentionDays();
	}

	@Override
	public double getElasticityDefault() {
		return Settings.getMarketElasticityDefault();
	}

	@Override
	public double getVolatilityDefault() {
		return Settings.getMarketVolatilityDefault();
	}

	@Override
	public double getReversionRate() {
		return Settings.getMarketReversionRate();
	}

	@Override
	public double getMinFloorMultiplier() {
		return Settings.getMarketMinFloorMultiplier();
	}

	@Override
	public double getMaxCeilingMultiplier() {
		return Settings.getMarketMaxCeilingMultiplier();
	}

	@Override
	public IndexWeighting getIndexWeighting() {
		String raw = Settings.getMarketIndexWeighting();
		if (raw == null) {
			return IndexWeighting.EQUAL;
		}
		return switch (raw.toLowerCase()) {
			case "volume" -> IndexWeighting.VOLUME;
			case "market_cap" -> IndexWeighting.MARKET_CAP;
			default -> IndexWeighting.EQUAL;
		};
	}

	@Override
	public Map<String, ItemOverride> getPerItemOverrides() {
		Map<String, ItemOverride> overrides = Settings.getMarketPerItemOverrides();
		return overrides == null ? Map.of() : overrides;
	}
}
