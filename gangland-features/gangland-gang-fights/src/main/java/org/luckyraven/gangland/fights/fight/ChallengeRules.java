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

	public static @Nullable String propose(FightSettings s, boolean sameGang, boolean allied, Party proposer,
	                                       Party target, int size, double bet) {
		return null;
	}

	public static @Nullable String accept(FightSettings s, Party acceptor, double bet) {
		return null;
	}

	public static @Nullable String enlist(FightSettings s, Candidate candidate, boolean ranked) {
		return null;
	}
}
