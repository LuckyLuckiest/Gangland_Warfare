package org.luckyraven.gangland.fights.fight;

/** The numbers the challenge-to-result flow reads, copied out of {@code fight_rules.yml} (see FightRules#settings). */
public record FightSettings(int challengeExpirySeconds, int joinWindowSeconds, int countdownSeconds,
                            int maxDurationMinutes, int resultSeconds, int minTeamSize, int maxTeamSize,
                            boolean requireEqualTeams, int declineCooldownSeconds, String timeoutResult,
                            int minMemberAgeHours, double maxBet, int maxBetPercentOfBank, double rankedMinBet,
                            int rankedMinMemberAgeHours, int rankedMinOpponentGangAgeDays) {

	/** A bet of at least {@code rankedMinBet} (and above zero) makes the fight ranked. */
	public boolean ranked(double bet) {
		return bet > 0 && bet >= rankedMinBet;
	}
}
