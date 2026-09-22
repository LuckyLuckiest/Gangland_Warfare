package org.luckyraven.gangland.file.configuration.identity;

import org.luckyraven.gangland.core.user.IdentitySettingsContract;
import org.luckyraven.gangland.file.configuration.Settings;

import java.math.BigDecimal;

/**
 * Routes {@link IdentitySettingsContract} calls from the identity slice ({@code User}/{@code Level}, gangland-core)
 * through the static {@link Settings} reader in gangland-impl, keeping identity code free of a direct Settings
 * import. Split out of {@code GanglandGangSettings} (WS5 G0, B2).
 */
public final class GanglandIdentitySettings implements IdentitySettingsContract {

	@Override
	public boolean isAutoSave() {
		return Settings.isAutoSave();
	}

	@Override
	public int getUserMaxLevel() {
		return Settings.getUserMaxLevel();
	}

	@Override
	public int getUserLevelBaseAmount() {
		return Settings.getUserLevelBaseAmount();
	}

	@Override
	public String getUserLevelFormula() {
		return Settings.getUserLevelFormula();
	}

	@Override
	public BigDecimal getBountyEachKillValue() {
		return Settings.getBountyEachKillValue();
	}

	@Override
	public double getBountyTimerMultiple() {
		return Settings.getBountyTimerMultiple();
	}

	@Override
	public double getBountyTimerMax() {
		return Settings.getBountyTimerMax();
	}

	@Override
	public boolean isBountyTimerEnabled() {
		return Settings.isBountyTimerEnabled();
	}

	@Override
	public int getWantedLevelIncrement() {
		return Settings.getWantedLevelIncrement();
	}

	@Override
	public int getWantedMaximumLevel() {
		return Settings.getWantedMaximumLevel();
	}

	@Override
	public boolean isWantedTimerEnabled() {
		return Settings.isWantedTimerEnabled();
	}
}
