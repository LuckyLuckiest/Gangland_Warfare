package org.luckyraven.gangland.core.user;

import java.math.BigDecimal;

/**
 * Configuration values the player-identity slice ({@code User}, {@code UserManager}, {@code Level}, and the
 * bounty/wanted value objects built inside {@code User}'s constructor) reads at runtime. Implemented in
 * gangland-impl by a bean that delegates to the {@code Settings} class, so identity code never imports
 * {@code Settings} directly.
 *
 * <p>Split out of {@code GangSettingsContract} (WS5 G0, B2): these 11 getters cover {@code settings.yml}'s
 * {@code User:}/{@code Bounty:}/{@code Wanted:} blocks, which stay core regardless of whether the gang module is
 * installed. The 3 remaining gang-display getters stayed on {@code GangSettingsContract}, moving to the gang module
 * in G1.
 */
public interface IdentitySettingsContract {

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
}
