package org.luckyraven.gangland.fights.fight;

import org.jetbrains.annotations.Nullable;

/** The pure challenge checks. Each returns the {@code fight_messages.yml} key of the refusal, or null to allow. */
public final class ChallengeRules {

	/** One side of a challenge as the rules see it. */
	public record Party(boolean mayFight, boolean mayWithdraw, double bank, long gangAgeDays) {
	}

	/** One would-be fighter as the rules see him. */
	public record Candidate(boolean tagged, boolean downed, boolean wanted, long memberAgeHours) {
	}

	private ChallengeRules() {
	}

	/** Same gang and allied are checked by the caller's lookups and passed in; size, rank, bet and ranked here. */
	public static @Nullable String propose(FightSettings s, boolean sameGang, boolean allied, Party proposer,
	                                       Party target, int size, double bet) {
		if (sameGang) return "Challenge.Same_Gang";
		if (allied) return "Challenge.Allied";
		if (size < s.minTeamSize() || size > s.maxTeamSize()) return "Challenge.Size_Range";
		if (!proposer.mayFight()) return "Challenge.Rank_Needed";
		if (bet < 0 || Double.isNaN(bet)) return "Challenge.Bet_Invalid";

		String betRefusal = bet(s, proposer, bet);
		if (betRefusal != null) return betRefusal;
		// the other bank must be able to match the stake; its officers re-check at accept
		if (bet > 0 && bet > target.bank() * s.maxBetPercentOfBank() / 100D) return "Challenge.Bet_Over_Bank";

		if (s.ranked(bet) && target.gangAgeDays() < s.rankedMinOpponentGangAgeDays()) {
			return "Challenge.Ranked_Gang_Too_Young";
		}
		return null;
	}

	public static @Nullable String accept(FightSettings s, Party acceptor, double bet) {
		if (!acceptor.mayFight()) return "Challenge.Rank_Needed";
		return bet(s, acceptor, bet);
	}

	/** A fight officer must not be a de facto Withdraw officer: any stake needs the Withdraw gate too. */
	private static @Nullable String bet(FightSettings s, Party party, double bet) {
		if (bet <= 0) return null;
		if (!party.mayWithdraw()) return "Challenge.Withdraw_Needed";
		if (bet > s.maxBet()) return "Challenge.Bet_Too_High";
		if (bet > party.bank() * s.maxBetPercentOfBank() / 100D) return "Challenge.Bet_Over_Bank";
		return null;
	}

	public static @Nullable String enlist(FightSettings s, Candidate candidate, boolean ranked) {
		if (candidate.tagged()) return "Challenge.Tagged";
		if (candidate.downed()) return "Challenge.Downed";
		if (candidate.wanted()) return "Challenge.Wanted";

		int required = ranked ? s.rankedMinMemberAgeHours() : s.minMemberAgeHours();
		if (candidate.memberAgeHours() < required) return "Challenge.Member_Too_Young";
		return null;
	}
}
