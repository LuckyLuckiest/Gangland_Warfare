package org.luckyraven.gangland.data.economy;

import java.math.BigDecimal;

/**
 * Seam 2: read-only view of a bank tier's caps, for core consumers that need tier numbers without naming a
 * Banker-NPC type. The cops-n-crooks {@code BankTier} record implements this verbatim (its component accessors
 * already match). See documentation/module-loader.md, "Core seams".
 */
public interface BankTierView {

	String id();

	String displayName();

	BigDecimal maxBalance();

	BigDecimal dailyDepositLimit();

	double deathLossDiscount();

	/**
	 * Fraction of the bank balance credited per 24 hours; read by the {@code bank_interest_rate} placeholder.
	 */
	double interestRate();

	/**
	 * Free grant deposited every 7 days ({@code 0} = disabled); read by the {@code bank_weekly_amount} placeholder.
	 */
	BigDecimal weeklyLoanAmount();

	/**
	 * Free grant deposited every 30 days ({@code 0} = disabled); read by the {@code bank_monthly_amount}
	 * placeholder.
	 */
	BigDecimal monthlyLoanAmount();
}
