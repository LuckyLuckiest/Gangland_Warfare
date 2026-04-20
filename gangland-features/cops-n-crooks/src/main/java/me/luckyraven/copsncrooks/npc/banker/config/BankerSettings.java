package me.luckyraven.copsncrooks.npc.banker.config;

public interface BankerSettings {

	int getHeadTrackRadius();

	double getMaxHealth();

	boolean isInvulnerable();

	String getFallbackTierId();

	double getDailyDepositLimit();

	double getDailyWithdrawLimit();

	/**
	 * Length of the rolling window (in seconds) after which the per-player deposit / withdraw counters reset. 86400
	 * behaves like "24 hours since the last reset" — fairer than a calendar-day rollover at midnight.
	 */
	long getResetPeriodSeconds();

	double getCreateFee();

	double getInitialBalance();

	double getRenameFee();

	double getDeleteFee();

	String getInventoryFillItem();

	String getInventoryFillName();

}
