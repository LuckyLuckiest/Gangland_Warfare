package me.luckyraven.gang.contract;

import java.math.BigDecimal;

/**
 * Configuration values the gang / user / rank / member domain reads at runtime. Implemented in gangland-impl by a bean
 * that delegates to the Settings class, so domain code never imports Settings directly.
 */
public interface GangSettingsContract {

	boolean isAutoSave();

	int getUserMaxLevel();

	int getUserLevelBaseAmount();

	String getUserLevelFormula();

	BigDecimal getBountyEachKillValue();

	double getBountyTimerMultiple();

	double getBountyTimerMax();

	boolean isBountyTimerEnabled();

	int getWantedLevelIncrement();

	int getWantedMaximumLevel();

	boolean isWantedTimerEnabled();

	String getGangDisplayNameChar();

	String getGangRankHead();

	String getGangRankTail();
}
