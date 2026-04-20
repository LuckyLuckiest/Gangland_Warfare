package me.luckyraven.copsncrooks.npc.banker.config;

import java.math.BigDecimal;

public interface BankerSettings {

	int getHeadTrackRadius();

	double getMaxHealth();

	boolean isInvulnerable();

	String getFallbackTierId();

	/**
	 * Length of the rolling window (in seconds) after which the per-player deposit counter resets. 86400 behaves like
	 * "24 hours since the last reset" — fairer than a calendar-day rollover at midnight.
	 */
	long getResetPeriodSeconds();

	BigDecimal getCreateFee();

	BigDecimal getInitialBalance();

	BigDecimal getRenameFee();

	String getInventoryFillItem();

	String getInventoryFillName();

}
