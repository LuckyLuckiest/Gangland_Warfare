package org.luckyraven.gangland.fights.fight;

import org.luckyraven.gangland.fights.FightRules;

import java.math.BigDecimal;

/**
 * The bet and ranked limits as a pure checker (no Bukkit, no banks): each check returns the {@code fight_messages.yml}
 * key of the refusal, or {@code null} when the fight may go on. The fight flow calls them at propose and again at
 * accept (the banks and the day totals move in between).
 */
public final class FightBetCaps {

	private final FightRules rules;

	public FightBetCaps(FightRules rules) {
		this.rules = rules;
	}

	/** Max_Bet, and Max_Bet_Percent_Of_Bank against BOTH banks. A bet of 0 is always fine. */
	public String checkBet(double bet, BigDecimal bankA, BigDecimal bankB) {
		if (bet <= 0) return null;
		if (bet > rules.getMaxBet()) return "Economy.Bet_Too_High";

		BigDecimal percent = BigDecimal.valueOf(rules.getMaxBetPercentOfBank());
		BigDecimal limitA  = bankA.multiply(percent).movePointLeft(2);
		BigDecimal limitB  = bankB.multiply(percent).movePointLeft(2);
		BigDecimal stake   = BigDecimal.valueOf(bet);
		if (stake.compareTo(limitA) > 0 || stake.compareTo(limitB) > 0) return "Economy.Bet_Over_Percent";
		return null;
	}

	/**
	 * Daily money flow. {@code pairNetToday} is how much one gang already took from the other today (absolute);
	 * {@code wonTodayA}/{@code wonTodayB} is each gang's total bet winnings today.
	 */
	public String checkTransfer(double bet, double pairNetToday, double wonTodayA, double wonTodayB) {
		if (bet <= 0) return null;
		if (Math.abs(pairNetToday) + bet > rules.getMaxNetTransferPerPairPerDay()) return "Economy.Pair_Cap";

		double cap = rules.getMaxBetTransferPerGangPerDay();
		if (wonTodayA + bet > cap || wonTodayB + bet > cap) return "Economy.Gang_Cap";
		return null;
	}

	/**
	 * Ranked only: opponent age, per-pair-per-day count and the rematch cooldown. {@code millisSinceLast} is
	 * Long.MAX_VALUE when the pair never fought ranked.
	 */
	public String checkRanked(long opponentAgeDays, int pairRankedToday, long millisSinceLast) {
		if (opponentAgeDays < rules.getRankedMinOpponentGangAgeDays()) return "Economy.Ranked_Opponent_Too_Young";
		if (pairRankedToday >= rules.getRankedPerPairPerDay()) return "Economy.Ranked_Pair_Limit";
		if (millisSinceLast < rules.getRematchCooldownMinutes() * 60_000L) return "Economy.Ranked_Rematch_Cooldown";
		return null;
	}
}
