package org.luckyraven.gangland.gang.bounty;

import java.math.BigDecimal;

/**
 * Provides bounty-timer configuration values to {@link BountyExecutor}.
 * <p>
 * Implementations live in {@code gangland-impl} and delegate to
 * {@code org.luckyraven.gangland.file.configuration.SettingAddon}, keeping {@code cops-n-crooks} decoupled from the
 * main plugin's file-loading infrastructure.
 */
public interface BountySettings {

	/**
	 * Seconds between each automatic bounty increase tick.
	 */
	int getTimeInterval();

	/**
	 * Base bounty value awarded per kill. Used as the seed value when the current bounty is zero.
	 */
	BigDecimal getEachKillValue();

	/**
	 * Multiplier applied each timer tick to grow the bounty exponentially.
	 */
	double getTimerMultiple();

	/**
	 * Hard cap; the timer stops once the bounty reaches or exceeds this value.
	 */
	double getTimerMax();
}
