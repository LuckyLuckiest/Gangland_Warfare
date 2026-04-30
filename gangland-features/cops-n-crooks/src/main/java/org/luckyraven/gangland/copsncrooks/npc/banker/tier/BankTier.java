package org.luckyraven.gangland.copsncrooks.npc.banker.tier;

import java.math.BigDecimal;

/**
 * Catalogue entry for one rung of the bank-account ladder. Currency fields are {@link BigDecimal} so tier caps can
 * extend beyond {@code double}'s {@code 2^53} precision ceiling; ratios stay {@code double}.
 *
 * @param dailyDepositLimit rolling-window deposit cap applied while the player is at this tier
 * @param interestRate fraction of bank balance credited per 24h (applied continuously on interaction; clamped so
 * 		balance never overflows {@link #maxBalance})
 * @param deathLossDiscount fraction in {@code [0, 1]} subtracted from the death-tax deduction for accounts at this
 * 		tier (0.5 = half the normal loss)
 * @param weeklyLoanAmount free grant deposited into the bank balance every 7 days ({@code 0} = disabled)
 * @param monthlyLoanAmount free grant deposited into the bank balance every 30 days ({@code 0} = disabled)
 */
public record BankTier(String id,
                       String displayName,
                       BigDecimal maxBalance,
                       BigDecimal upgradeCost,
                       int order,
                       BigDecimal dailyDepositLimit,
                       double interestRate,
                       double deathLossDiscount,
                       BigDecimal weeklyLoanAmount,
                       BigDecimal monthlyLoanAmount) {
}
