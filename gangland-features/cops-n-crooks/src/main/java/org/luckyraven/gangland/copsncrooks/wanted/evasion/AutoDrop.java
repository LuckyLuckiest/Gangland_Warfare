package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;

/**
 * The value types {@code Drop_Mode: AUTO} passes between the clock, the chase arcs, the learner and the planner.
 *
 * @since 0.15.2
 */
public final class AutoDrop {

	private AutoDrop() {
	}

	/** How a completed evasion ended, which picks the star card. */
	public enum Ending {
		STILL_HOT, PETTY, COLD_TRAIL, CLEAN_BREAK, HUNKER_DOWN
	}

	/** The chase as a whole; times are in milliseconds with offline time removed. */
	public record ChaseView(int crimes, int opening, boolean copKilled, int peak, long chaseMs, long quietMs,
	                        int quits, int respots, int recentEnds) {
	}

	/** The current search spell. */
	public record SpellView(long insideMs, long outsideMs, int steps, boolean narrow, boolean teleported) {
	}

	/**
	 * What the server has learned.
	 *
	 * @param delta           the player's escape habit, -1..1 (0 = stranger).
	 * @param typicalSeconds the typical getaway length at the chase's peak.
	 */
	public record Learned(double delta, double typicalSeconds) {

		/** A stranger on a fresh server: no habit, the configured typical time. */
		public static Learned cold(AutoSettings settings, int peak) {
			return new Learned(0, settings.typicalFor(peak));
		}
	}

	/**
	 * The planner's verdict.
	 *
	 * @param stars  stars to drop, 1..level.
	 * @param ending which rule decided.
	 * @param reason a short id for the card and the debug line (for example {@code rampage}, {@code logout}).
	 */
	public record DropPlan(int stars, Ending ending, String reason) {
	}
}
