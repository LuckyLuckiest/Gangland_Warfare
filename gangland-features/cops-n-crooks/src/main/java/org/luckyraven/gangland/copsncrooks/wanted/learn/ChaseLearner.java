package org.luckyraven.gangland.copsncrooks.wanted.learn;

import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.DropMode;
import org.luckyraven.gangland.copsncrooks.wanted.config.EvasionSettings;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What {@code Drop_Mode: AUTO} learns from finished chases: per player an escape habit ({@link #delta}), per peak level
 * how long the cops typically stay on a player's tail ({@link #typicalSeconds}). Updated once per chase by
 * {@link #record}, by one fixed rule, and saved through the two repositories' autosave. The caches hold immutable rows
 * replaced whole, so the async autosave never sees half an update. {@code Learning.Enable: false} is read live: every
 * method then answers cold values and writes nothing, and the caches stay as they were.
 *
 * <p>Unlike {@code JailExitService} it reads the tables on the <b>first</b> load only: a {@code /glw reload} would
 * otherwise replace learning that autosave has not written yet with the older stored rows.
 *
 * @since 0.15.2
 */
public class ChaseLearner implements BeanLifecycle {

	// ponytail: the four constants below are not keys; promote them if an admin ever needs to tune them
	/** Decay of the server rows per counted chase. */
	private static final double SERVER_DECAY        = 0.99;
	/** Share of the running median one getaway moves it by. */
	private static final double MEDIAN_STEP         = 0.10;
	/** A player with more than this many counted chases weighs {@code FAIR_SHARE / n} on the server rows. */
	private static final double FAIR_SHARE          = 4;
	/** Chases of evidence the configured guesses are worth against the server rows. */
	private static final double SERVER_PRIOR_CHASES = 20;
	private static final double MAX_TYPICAL_COUNT   = 100;

	private static final long DAY_MS = 86_400_000L;

	private final ChaseConfigLoader            config;
	private final Map<UUID, ChaseHabit>        habits = new ConcurrentHashMap<>();
	private final Map<Integer, ChaseLevelStat> levels = new ConcurrentHashMap<>();
	private final IRepository<ChaseHabit>      habitRepository;
	private final IRepository<ChaseLevelStat>  levelRepository;

	public ChaseLearner(ChaseConfigLoader config, IRepository<ChaseHabit> habitRepository,
	                    IRepository<ChaseLevelStat> levelRepository) {
		this.config          = config;
		this.habitRepository = habitRepository;
		this.levelRepository = levelRepository;

		habitRepository.setDataSupplier(habits::values);
		levelRepository.setDataSupplier(levels::values);
	}

	/**
	 * First load only: reads both tables into the caches, then deletes the rows not updated for
	 * {@code Forget_After_Days} (and keeps them out of the caches). The delete is skipped with learning off, which
	 * writes nothing.
	 */
	@Override
	public void onInitialize(boolean firstLoad) {
		if (!firstLoad) return;

		AutoSettings.Learning learn = config.get().evasion().auto().learning();
		long                  limit = System.currentTimeMillis() - learn.forgetAfterDays() * DAY_MS;

		for (ChaseHabit row : habitRepository.loadAll()) {
			if (learn.enable() && row.lastAt() < limit) {
				habitRepository.delete(row);
			} else {
				habits.put(row.player(), row);
			}
		}
		for (ChaseLevelStat row : levelRepository.loadAll()) {
			if (learn.enable() && row.updatedAt() < limit) {
				levelRepository.delete(row);
			} else {
				levels.put(row.level(), row);
			}
		}
	}

	@Override
	public void onClear() {
		// The caches hold learning the autosave has not written yet; a reload must not drop it.
	}

	/** Puts stored rows into the caches, replacing any row with the same key. */
	void load(Collection<ChaseHabit> habitRows, Collection<ChaseLevelStat> levelRows) {
		habitRows.forEach(row -> habits.put(row.player(), row));
		levelRows.forEach(row -> levels.put(row.level(), row));
	}

	/**
	 * Learns from one finished chase (PLAN.md section 3.5). Ignored unless the mode is AUTO with learning on, the chase
	 * started with a crime, lasted {@code Min_Chase_Seconds}, ended in a counted way, and the player's previous counted
	 * chase is at least {@code Min_Seconds_Between_Outcomes} old.
	 */
	public void record(ChaseRecord chase, long now) {
		EvasionSettings evasion = config.get().evasion();
		AutoSettings    auto    = evasion.auto();
		AutoSettings.Learning learn = auto.learning();

		if (evasion.dropMode() != DropMode.AUTO || !learn.enable()) return;
		if (chase.startCause() != WantedCause.CRIME) return;
		if (chase.chaseMs() < learn.minChaseSeconds() * 1000L) return;

		double outcome = outcome(chase.endCause(), chase.peak());
		if (Double.isNaN(outcome)) return;

		ChaseHabit before = habits.get(chase.player());
		if (before != null && now - before.lastAt() < learn.minSecondsBetweenOutcomes() * 1000L) return;

		int            peak  = chase.peak();
		ChaseLevelStat level = levels.get(peak);
		double         sN    = level == null ? 0 : level.n();
		double         sEsc  = level == null ? 0 : level.escaped();

		// the server's expectation for this peak, read before this chase is added
		double expected = (sEsc + SERVER_PRIOR_CHASES * auto.escapeRateFor(peak)) / (sN + SERVER_PRIOR_CHASES);

		double d  = learn.decayPerChase();
		double hN = before == null ? 0 : before.n();
		habits.put(chase.player(), new ChaseHabit(chase.player(), hN * d + 1,
		                                          (before == null ? 0 : before.actual()) * d + outcome,
		                                          (before == null ? 0 : before.expected()) * d + expected, now));

		double weight  = Math.min(1, FAIR_SHARE / Math.max(1, hN));
		double typical = level == null ? 0 : level.typicalSeconds();
		double count   = level == null ? 0 : level.typicalCount();
		if (outcome == 1) {
			double contact = chase.contactMs() / 1000.0;
			typical = count == 0 ? contact : typical + Math.signum(contact - typical) * MEDIAN_STEP * typical * weight;
			count   = Math.min(MAX_TYPICAL_COUNT, count + weight);
		}
		levels.put(peak, new ChaseLevelStat(peak, sN * SERVER_DECAY + weight, sEsc * SERVER_DECAY + weight * outcome,
		                                    typical, count, now));
	}

	/** The player's escape habit, -1..1; 0 for a stranger or with learning off. */
	public double delta(UUID player) {
		AutoSettings.Learning learn = config.get().evasion().auto().learning();
		ChaseHabit habit = habits.get(player);
		if (!learn.enable() || habit == null) return 0;

		double delta = (habit.actual() - habit.expected()) / (habit.n() + learn.priorChases());
		return Math.max(-1, Math.min(1, delta));
	}

	/**
	 * The typical getaway length at {@code peak}, in seconds: the learned contact time blended with the configured
	 * {@code Typical_Seconds} and kept within half..double of it; the configured value alone with learning off.
	 */
	public double typicalSeconds(int peak, AutoSettings settings) {
		double         guess = settings.typicalFor(peak);
		ChaseLevelStat level = levels.get(peak);
		if (!settings.learning().enable() || level == null) return guess;

		double c       = level.typicalCount();
		double blended = (level.typicalSeconds() * c + guess * SERVER_PRIOR_CHASES) / (c + SERVER_PRIOR_CHASES);
		return Math.max(0.5 * guess, Math.min(2 * guess, blended));
	}

	/** 1 escaped, 0.5 the safety-net timer ended it, 0 caught at 2+ stars; NaN when the end is not learned from. */
	private static double outcome(WantedCause end, int peak) {
		return switch (end) {
			case EVASION -> 1;
			case DECAY -> 0.5;
			// a 1-star catch is the cheapest chase there is, so it would be the strongest (and a farmable) signal
			case ARREST, BRIBE -> peak >= 2 ? 0 : Double.NaN;
			// DEATH resets however he died (a fall, lava), so it says nothing about the cops
			default -> Double.NaN;
		};
	}
}
