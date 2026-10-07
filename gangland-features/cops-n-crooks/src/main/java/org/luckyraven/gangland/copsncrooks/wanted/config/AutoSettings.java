package org.luckyraven.gangland.copsncrooks.wanted.config;

import java.util.List;

/**
 * {@code Wanted.Evasion.Auto} of {@code copsncrooks/wanted.yml}: how {@link DropMode#AUTO} judges a chase. Read only while
 * the drop mode is AUTO; a file without the block reads as {@link #DEFAULT}.
 *
 * @param openingSeconds    seconds from the first crime that count as the chase's opening.
 * @param rampageCrimes     crimes in the opening that make a chase a rampage.
 * @param rampagePeakLevel  peak stars that make a chase a rampage.
 * @param lockCoolSeconds   quiet seconds before a rampage or a logout lock lifts.
 * @param respotLimit       times the cops may spot the player again before small chases and clean breaks stop paying.
 * @param petty             the small-chase rule.
 * @param coldTrail         the long-quiet-chase rule.
 * @param cleanBreak        the left-the-zone rule.
 * @param momentum          how the next timer shrinks after a drop.
 * @param repeatChases      recent crime chases that make a known face (0 = off).
 * @param repeatWindowMinutes how far back those chases count.
 * @param learning          learning from finished chases.
 * @param rampageMinWeight  a crime counts toward the rampage opening only when its heat weight is at least this.
 * @since 0.15.2
 */
public record AutoSettings(int openingSeconds, int rampageCrimes, int rampagePeakLevel, int lockCoolSeconds,
                           int respotLimit, Petty petty, ColdTrail coldTrail, CleanBreak cleanBreak,
                           Momentum momentum, int repeatChases, int repeatWindowMinutes, Learning learning, int rampageMinWeight) {

	/** The 0.15.2 shape: every crime weighing at least 80 counts toward the rampage opening. */
	public AutoSettings(int openingSeconds, int rampageCrimes, int rampagePeakLevel, int lockCoolSeconds,
	                    int respotLimit, Petty petty, ColdTrail coldTrail, CleanBreak cleanBreak, Momentum momentum,
	                    int repeatChases, int repeatWindowMinutes, Learning learning) {
		this(openingSeconds, rampageCrimes, rampagePeakLevel, lockCoolSeconds, respotLimit, petty, coldTrail, cleanBreak,
		     momentum, repeatChases, repeatWindowMinutes, learning, 80);
	}


	/** The shipped {@code Wanted.Evasion.Auto}. */
	public static final AutoSettings DEFAULT = new AutoSettings(30, 4, 4, 180, 4, new Petty(2, 2),
	                                                            new ColdTrail(List.of(30, 60, 90, 120, 150), 2.0, 90),
	                                                            new CleanBreak(0.5, 0.5),
	                                                            new Momentum(0.75, 0.5, 20, 0.4), 3, 30,
	                                                            new Learning(true, List.of(0.90, 0.75, 0.55, 0.35, 0.20),
	                                                                         5, 0.90, 0.20, 0.8, 0.6, 1.6, 30, 180, 90),
	                                                            80);

	/**
	 * @param maxCrimes    most crimes a small chase may hold (a chase with none never counts).
	 * @param maxPeakLevel highest level a small chase may reach.
	 */
	public record Petty(int maxCrimes, int maxPeakLevel) {
	}

	/**
	 * @param typicalSeconds a fresh server's guess of the getaway length per peak level.
	 * @param ratio          times the typical time the whole chase must have lasted.
	 * @param quietSeconds   seconds with no new crime and no new star.
	 */
	public record ColdTrail(List<Integer> typicalSeconds, double ratio, int quietSeconds) {
	}

	/**
	 * @param outsideRatio share of the search time spent outside the zone (0 to 1).
	 * @param dropFraction share of the stars that drop (0 to 1), rounded up, at least one.
	 */
	public record CleanBreak(double outsideRatio, double dropFraction) {
	}

	/**
	 * @param stepSpeed         each step's share of the previous timer.
	 * @param narrowStepSpeed   the same after a narrow escape.
	 * @param narrowSeenSeconds seconds in sight before breaking away that make an escape narrow.
	 * @param floor             no timer shrinks below this share of {@code Seconds_To_Drop}.
	 */
	public record Momentum(double stepSpeed, double narrowStepSpeed, int narrowSeenSeconds, double floor) {
	}

	/**
	 * @param enable                    {@code false} stores and reads nothing.
	 * @param escapeRate                a fresh server's getaway rate per peak level.
	 * @param priorChases               chases of evidence before a habit counts fully.
	 * @param decayPerChase             how much an older chase still counts after each newer one.
	 * @param habitualEscaperDelta      the habit that makes a known face.
	 * @param habitTimeStrength         how strongly the habit stretches or shortens the timer.
	 * @param minTimeFactor             the habit alone never shortens a timer below this share.
	 * @param maxTimeFactor             no timer is stretched above this share.
	 * @param minChaseSeconds           shorter chases are not learned from.
	 * @param minSecondsBetweenOutcomes closer chases of one player are not learned from.
	 * @param forgetAfterDays           rows not updated for this many days are deleted at startup.
	 */
	public record Learning(boolean enable, List<Double> escapeRate, int priorChases, double decayPerChase,
	                       double habitualEscaperDelta, double habitTimeStrength, double minTimeFactor,
	                       double maxTimeFactor, int minChaseSeconds, int minSecondsBetweenOutcomes,
	                       int forgetAfterDays) {
	}

	/** Typical getaway seconds at {@code peak}, the level clamped into the list. */
	public int typicalFor(int peak) {
		List<Integer> list = coldTrail.typicalSeconds();
		return list.get(Math.max(1, Math.min(peak, list.size())) - 1);
	}

	/** Fresh-server escape rate at {@code peak}, the level clamped into the list. */
	public double escapeRateFor(int peak) {
		List<Double> list = learning.escapeRate();
		return list.get(Math.max(1, Math.min(peak, list.size())) - 1);
	}
}
