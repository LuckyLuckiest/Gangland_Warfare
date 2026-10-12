package org.luckyraven.gangland.fights.fight;

import org.jetbrains.annotations.Nullable;

/** The config-driven limits the flow checks at challenge and accept, behind a seam so the state machine stays pure. */
public interface FightLimits {

	/**
	 * The {@code fight_messages.yml} key of the refusal, or null to allow. {@code gangA} is the proposer; {@code accept}
	 * is true when the target is accepting (the banks and day totals may have moved since the proposal).
	 */
	@Nullable String refuse(int gangA, int gangB, double bet, boolean ranked, boolean accept);

	/** How long an arena stays closed after a fight ends there. */
	long arenaCooldownMillis();
}
