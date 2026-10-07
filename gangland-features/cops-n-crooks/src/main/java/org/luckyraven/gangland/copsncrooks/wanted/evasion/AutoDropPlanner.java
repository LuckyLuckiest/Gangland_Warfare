package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.ChaseView;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.DropPlan;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Ending;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Learned;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.SpellView;

/**
 * {@code Drop_Mode: AUTO}: how many stars a completed evasion takes, and how long the next hide timer is. Pure, no
 * Bukkit; the same views always give the same answer.
 *
 * @since 0.15.2
 */
public final class AutoDropPlanner {

	/** {@link Ending#STILL_HOT}: the chase was a rampage (busy opening, high peak or a cop killed). */
	public static final String REASON_RAMPAGE    = "rampage";
	/** {@link Ending#STILL_HOT}: the player logged out mid-chase. */
	public static final String REASON_LOGOUT     = "logout";
	/** {@link Ending#HUNKER_DOWN}: a repeat offender or a habitual escaper; picks the known-face card. */
	public static final String REASON_KNOWN_FACE = "known_face";
	/** {@link Ending#HUNKER_DOWN}: a narrow escape; picks the narrow card. */
	public static final String REASON_NARROW     = "narrow";
	/** No reason beyond the ending itself. */
	public static final String REASON_NONE       = "";

	private AutoDropPlanner() {
	}

	/**
	 * The drop table: the first matching rule decides. The result is always {@code 1..level}.
	 *
	 * @param level the stars the player holds now, at least 1.
	 */
	public static DropPlan plan(AutoSettings s, ChaseView c, SpellView sp, Learned l, int level) {
		boolean knownFace = knownFace(s, c, l);
		boolean respotsOk = c.respots() <= s.respotLimit();

		if (locked(s, c)) {
			return drop(1, level, Ending.STILL_HOT, rampage(s, c) ? REASON_RAMPAGE : REASON_LOGOUT);
		}
		if (c.crimes() >= 1 && c.crimes() <= s.petty().maxCrimes() && c.peak() <= s.petty().maxPeakLevel() &&
			!knownFace && respotsOk) {
			return drop(level, level, Ending.PETTY, REASON_NONE);
		}
		if (c.chaseMs() >= s.coldTrail().ratio() * l.typicalSeconds() * 1000 &&
			c.quietMs() >= s.coldTrail().quietSeconds() * 1000L) {
			return drop(level, level, Ending.COLD_TRAIL, REASON_NONE);
		}
		if (outsideRatio(sp) >= s.cleanBreak().outsideRatio() && !sp.teleported() && respotsOk && !knownFace) {
			int stars = (int) Math.ceil(level * s.cleanBreak().dropFraction());
			return drop(stars, level, Ending.CLEAN_BREAK, REASON_NONE);
		}

		String reason = knownFace ? REASON_KNOWN_FACE : sp.narrow() ? REASON_NARROW : REASON_NONE;
		return drop(1, level, Ending.HUNKER_DOWN, reason);
	}

	/**
	 * The hide timer's share of {@code Seconds_To_Drop}: momentum (none while locked) times the learned habit, clamped
	 * to {@code Momentum.Floor .. Learning.Max_Time_Factor}.
	 */
	public static double factor(AutoSettings s, ChaseView c, SpellView sp, Learned l) {
		AutoSettings.Momentum m = s.momentum();
		AutoSettings.Learning learning = s.learning();

		double speed = locked(s, c) ? 1.0 : sp.narrow() ? m.narrowStepSpeed() : m.stepSpeed();
		double step = Math.pow(speed, sp.steps());
		double habit = clamp(1 + learning.habitTimeStrength() * l.delta(), learning.minTimeFactor(),
		                     learning.maxTimeFactor());

		return clamp(step * habit, m.floor(), learning.maxTimeFactor());
	}

	private static boolean rampage(AutoSettings s, ChaseView c) {
		return c.opening() >= s.rampageCrimes() || c.peak() >= s.rampagePeakLevel() || c.copKilled();
	}

	private static boolean locked(AutoSettings s, ChaseView c) {
		return (rampage(s, c) || c.quits() > 0) && c.quietMs() < s.lockCoolSeconds() * 1000L;
	}

	private static boolean knownFace(AutoSettings s, ChaseView c, Learned l) {
		return (s.repeatChases() > 0 && c.recentEnds() >= s.repeatChases()) ||
			   l.delta() >= s.learning().habitualEscaperDelta();
	}

	private static double outsideRatio(SpellView sp) {
		long total = sp.insideMs() + sp.outsideMs();
		return total == 0 ? 0 : (double) sp.outsideMs() / total;
	}

	private static DropPlan drop(int stars, int level, Ending ending, String reason) {
		return new DropPlan(Math.max(1, Math.min(stars, level)), ending, reason);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
