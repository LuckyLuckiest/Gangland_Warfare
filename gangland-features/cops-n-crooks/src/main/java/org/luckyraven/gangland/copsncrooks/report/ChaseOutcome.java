package org.luckyraven.gangland.copsncrooks.report;

/**
 * How a 0.12 pursuit ended, resolved by {@link ChaseRecord#resolve(boolean, long)} once the chase's
 * {@code WantedEndEvent} fires.
 */
public enum ChaseOutcome {

	/** Wanted dropped to zero through evasion, decay, or an admin clear — the player was never restrained. */
	ESCAPED,
	/** The player is (or was, within the busted grace window) handcuffed or jailed when the chase ended. */
	BUSTED,
	/** The player died or was downed at some point during the chase. */
	WASTED

}
