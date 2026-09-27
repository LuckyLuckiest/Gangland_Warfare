package org.luckyraven.gangland.copsncrooks.report;

import lombok.Getter;

/**
 * Per-chase bookkeeping for the 0.12 pursuit report: started when a player becomes wanted, updated as their stars
 * rise and cops die, and resolved into a {@link ChaseOutcome} once the chase's {@code WantedEndEvent} fires. Plain
 * data — no Bukkit dependency, so it is fully unit-testable.
 */
@Getter
public class ChaseRecord {

	/**
	 * How long after being cuffed the chase still resolves as {@link ChaseOutcome#BUSTED} once it ends — covers the
	 * gap between {@code CuffedEvent} and the wanted level actually clearing.
	 */
	static final long BUSTED_GRACE_MILLIS = 30_000L;

	private final long startedAt;

	private int     maxStars;
	private int     copsKilled;
	private boolean wasted;
	private long    cuffedAt;

	public ChaseRecord(long startedAt) {
		this.startedAt = startedAt;
	}

	/**
	 * Raises {@link #maxStars} if {@code level} is higher than what is already recorded; otherwise a no-op.
	 *
	 * @param level the wanted level just reached
	 */
	public void raiseStars(int level) {
		if (level > maxStars) maxStars = level;
	}

	/**
	 * Credits a cop kill to this chase.
	 */
	public void copKilled() {
		copsKilled++;
	}

	/**
	 * Marks the chase as ending in death or downed. Sticky: once set, {@link #resolve} always returns
	 * {@link ChaseOutcome#WASTED}, even if the wanted level is later cleared some other way.
	 */
	public void markWasted() {
		this.wasted = true;
	}

	/**
	 * Records the moment the player was cuffed, for the {@link #BUSTED_GRACE_MILLIS} window {@link #resolve} checks.
	 *
	 * @param now the current time, epoch milliseconds
	 */
	public void markCuffed(long now) {
		this.cuffedAt = now;
	}

	/**
	 * Resolves the chase's outcome.
	 *
	 * @param restrained whether the player is currently handcuffed or jailed
	 * @param now the current time, epoch milliseconds
	 *
	 * @return {@link ChaseOutcome#WASTED} if {@link #markWasted()} was ever called; otherwise
	 * {@link ChaseOutcome#BUSTED} if {@code restrained} or the player was cuffed within the last
	 * {@link #BUSTED_GRACE_MILLIS}; otherwise {@link ChaseOutcome#ESCAPED} (evasion, decay, or an admin clear)
	 */
	public ChaseOutcome resolve(boolean restrained, long now) {
		if (wasted) return ChaseOutcome.WASTED;
		if (restrained || (cuffedAt > 0 && now - cuffedAt <= BUSTED_GRACE_MILLIS)) return ChaseOutcome.BUSTED;
		return ChaseOutcome.ESCAPED;
	}

	/**
	 * @param now the current time, epoch milliseconds
	 *
	 * @return the chase's duration so far, in whole seconds, never negative
	 */
	public long durationSeconds(long now) {
		return Math.max(0L, (now - startedAt) / 1000L);
	}

}
