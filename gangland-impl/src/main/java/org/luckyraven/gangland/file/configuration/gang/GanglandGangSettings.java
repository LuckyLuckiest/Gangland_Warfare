package org.luckyraven.gangland.file.configuration.gang;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.contract.GangSettingsContract;

import java.math.BigDecimal;

/**
 * Routes {@link GangSettingsContract} calls from the gang module through the static {@link Settings} reader in
 * gangland-impl, keeping the gang module free of a direct Settings import.
 */
public final class GanglandGangSettings implements GangSettingsContract {

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

	@Override
	public String getGangDisplayNameChar() {
		return Settings.getGangDisplayNameChar();
	}

	@Override
	public String getGangRankHead() {
		return Settings.getGangRankHead();
	}

	@Override
	public String getGangRankTail() {
		return Settings.getGangRankTail();
	}
}
