package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.ChaseView;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.DropPlan;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Ending;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Learned;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.SpellView;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Ending.CLEAN_BREAK;
import static org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Ending.COLD_TRAIL;
import static org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Ending.HUNKER_DOWN;
import static org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Ending.PETTY;
import static org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Ending.STILL_HOT;

/**
 * {@link AutoDropPlanner}: the worked examples of PLAN.md section 3.6 (E1-E8 on a cold-start server, W2-W3 warm) as one
 * table, then each rule of the drop table (3.3) and the timer factor (3.4) at its edges. Shipped {@link AutoSettings}
 * throughout unless a test says otherwise.
 */
@DisplayName("AutoDropPlanner")
class AutoDropPlannerTest {

	private static final AutoSettings S = AutoSettings.DEFAULT;

	/** A quiet, one-crime, two-star chase nobody knows; each test bends the one field it is about. */
	private static ChaseView chase(int crimes, int opening, boolean copKilled, int peak, double chaseS, double quietS,
	                               int quits, int respots, int recentEnds) {
		return new ChaseView(crimes, opening, copKilled, peak, ms(chaseS), ms(quietS), quits, respots, recentEnds);
	}

	private static SpellView spell(double insideS, double outsideS, int steps, boolean narrow, boolean teleported) {
		return new SpellView(ms(insideS), ms(outsideS), steps, narrow, teleported);
	}

	private static long ms(double seconds) {
		return Math.round(seconds * 1000);
	}

	private static Learned cold(int peak) {
		return Learned.cold(S, peak);
	}

	/** Habit {@code delta}, typical time the cold guess. */
	private static Learned habit(double delta, int peak) {
		return new Learned(delta, S.typicalFor(peak));
	}

	private static AutoSettings withCleanBreakFraction(double fraction) {
		return new AutoSettings(S.openingSeconds(), S.rampageCrimes(), S.rampagePeakLevel(), S.lockCoolSeconds(),
		                        S.respotLimit(), S.petty(), S.coldTrail(),
		                        new AutoSettings.CleanBreak(S.cleanBreak().outsideRatio(), fraction), S.momentum(),
		                        S.repeatChases(), S.repeatWindowMinutes(), S.learning());
	}

	private static AutoSettings withRepeatChases(int repeatChases) {
		return new AutoSettings(S.openingSeconds(), S.rampageCrimes(), S.rampagePeakLevel(), S.lockCoolSeconds(),
		                        S.respotLimit(), S.petty(), S.coldTrail(), S.cleanBreak(), S.momentum(), repeatChases,
		                        S.repeatWindowMinutes(), S.learning());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Section 3.6: one row per drop decision, with the timer factor of the spell that row's view describes.
	// ---------------------------------------------------------------------------------------------------------------

	static Stream<Arguments> workedExamples() {
		return Stream.of(
				// E1 small fry: 2 crimes in the opening, peak 2, drop at 48 s after a 20 s hide inside the zone.
				Arguments.of("E1 small fry", chase(2, 2, false, 2, 48, 38, 0, 0, 0), spell(20, 0, 0, false, false),
				             cold(2), 2, 2, PETTY, 1.0),

				// E2 hunker cascade: peak 3, hides inside; 30 s, then 15 s, then 5.625 s.
				Arguments.of("E2 drop 1", chase(2, 1, false, 3, 93, 53, 0, 0, 0), spell(30, 0, 0, false, false),
				             cold(3), 3, 1, HUNKER_DOWN, 1.0),
				Arguments.of("E2 drop 2", chase(2, 1, false, 3, 108, 68, 0, 0, 0), spell(45, 0, 1, false, false),
				             cold(3), 2, 1, HUNKER_DOWN, 0.75),
				Arguments.of("E2 drop 3", chase(2, 1, false, 3, 113.625, 73.625, 0, 0, 0),
				             spell(50.625, 0, 2, false, false), cold(3), 1, 1, HUNKER_DOWN, 0.5625),

				// E3 clean break: 6 s inside, 12 s outside (24 s of progress at speed 2); then 3.75 s more outside.
				Arguments.of("E3 drop 1", chase(2, 1, false, 3, 81, 41, 0, 0, 0), spell(6, 12, 0, false, false),
				             cold(3), 3, 2, CLEAN_BREAK, 1.0),
				Arguments.of("E3 drop 2", chase(2, 1, false, 3, 84.75, 44.75, 0, 0, 0),
				             spell(6, 15.75, 1, false, false), cold(3), 1, 1, CLEAN_BREAK, 0.75),

				// E4 N of 5: cop killed but 205 s quiet, so unlocked; 4 s in, 28 s out, two respots.
				Arguments.of("E4 drop 1", chase(3, 2, true, 5, 265, 205, 0, 2, 0), spell(4, 28, 0, false, false),
				             cold(5), 5, 3, CLEAN_BREAK, 1.0),
				Arguments.of("E4 drop 2", chase(3, 2, true, 5, 272.5, 212.5, 0, 2, 0),
				             spell(4, 35.5, 1, false, false), cold(5), 2, 1, CLEAN_BREAK, 0.75),
				Arguments.of("E4 drop 3", chase(3, 2, true, 5, 275.3, 215.3, 0, 2, 0),
				             spell(4, 38.3, 2, false, false), cold(5), 1, 1, CLEAN_BREAK, 0.5625),

				// E5 cold trail: cop killer at 4 stars, quiet 338 s, chase 428 s >= 2 x 120 s.
				Arguments.of("E5 cold trail", chase(3, 1, true, 4, 428, 338, 0, 3, 0), spell(45, 0, 0, false, false),
				             cold(4), 4, 4, COLD_TRAIL, 1.0),

				// E6 still hot: the same cop killer hides right after his last crime; locked at every drop, no momentum.
				Arguments.of("E6 drop 1", chase(3, 1, true, 4, 138, 48, 0, 0, 0), spell(45, 0, 0, false, false),
				             cold(4), 4, 1, STILL_HOT, 1.0),
				Arguments.of("E6 drop 2", chase(3, 1, true, 4, 168, 78, 0, 0, 0), spell(75, 0, 1, false, false),
				             cold(4), 3, 1, STILL_HOT, 1.0),
				Arguments.of("E6 drop 3", chase(3, 1, true, 4, 188, 98, 0, 0, 0), spell(95, 0, 2, false, false),
				             cold(4), 2, 1, STILL_HOT, 1.0),
				Arguments.of("E6 drop 4", chase(3, 1, true, 4, 198, 108, 0, 0, 0), spell(105, 0, 3, false, false),
				             cold(4), 1, 1, STILL_HOT, 1.0),

				// E7 logout: E1 with one quit; quiet 33 s < 180 s, so locked, and 10 s later the last star alone.
				Arguments.of("E7 drop 1", chase(2, 2, false, 2, 43, 33, 1, 0, 0), spell(20, 0, 0, false, false),
				             cold(2), 2, 1, STILL_HOT, 1.0),
				Arguments.of("E7 drop 2", chase(2, 2, false, 2, 53, 43, 1, 0, 0), spell(30, 0, 1, false, false),
				             cold(2), 1, 1, STILL_HOT, 1.0),

				// E8 admin chase: no crimes, so never petty; hunker with momentum 30 / 15 / 5.625 s.
				Arguments.of("E8 drop 1", chase(0, 0, false, 3, 30, 30, 0, 0, 0), spell(30, 0, 0, false, false),
				             cold(3), 3, 1, HUNKER_DOWN, 1.0),
				Arguments.of("E8 drop 2", chase(0, 0, false, 3, 45, 45, 0, 0, 0), spell(45, 0, 1, false, false),
				             cold(3), 2, 1, HUNKER_DOWN, 0.75),
				Arguments.of("E8 drop 3", chase(0, 0, false, 3, 50.625, 50.625, 0, 0, 0),
				             spell(50.625, 0, 2, false, false), cold(3), 1, 1, HUNKER_DOWN, 0.5625),

				// W2 habitual escaper (delta 0.213): E1 rerun, no PETTY; 20 x 1.17 = 23.4 s, then 10 x 0.75 x 1.17.
				Arguments.of("W2 drop 1", chase(2, 2, false, 2, 51.4, 41.4, 0, 0, 0), spell(23.4, 0, 0, false, false),
				             habit(0.213, 2), 2, 1, HUNKER_DOWN, 1.1704),
				Arguments.of("W2 drop 2", chase(2, 2, false, 2, 60.2, 50.2, 0, 0, 0), spell(32.2, 0, 1, false, false),
				             habit(0.213, 2), 1, 1, HUNKER_DOWN, 0.8778),

				// W3 habitual loser (delta -0.26, habit 0.792): E2 rerun at 23.76 s, 11.88 s, 4.455 s.
				Arguments.of("W3 drop 1", chase(2, 1, false, 3, 86.8, 46.8, 0, 0, 0), spell(23.8, 0, 0, false, false),
				             habit(-0.26, 3), 3, 1, HUNKER_DOWN, 0.792),
				Arguments.of("W3 drop 2", chase(2, 1, false, 3, 98.7, 58.7, 0, 0, 0), spell(35.7, 0, 1, false, false),
				             habit(-0.26, 3), 2, 1, HUNKER_DOWN, 0.594),
				Arguments.of("W3 drop 3", chase(2, 1, false, 3, 103.2, 63.2, 0, 0, 0), spell(40.2, 0, 2, false, false),
				             habit(-0.26, 3), 1, 1, HUNKER_DOWN, 0.4455));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("workedExamples")
	@DisplayName("section 3.6 worked examples: stars, ending and timer factor")
	void workedExample(String name, ChaseView chase, SpellView spell, Learned learned, int level, int stars,
	                   Ending ending, double factor) {
		DropPlan plan = AutoDropPlanner.plan(S, chase, spell, learned, level);

		assertEquals(stars, plan.stars(), "stars");
		assertEquals(ending, plan.ending(), "ending");
		assertEquals(factor, AutoDropPlanner.factor(S, chase, spell, learned), 0.001, "factor");
	}

	@Test
	@DisplayName("worked examples carry the reason the card and debug line read")
	void workedExampleReasons() {
		assertEquals(AutoDropPlanner.REASON_RAMPAGE,
		             AutoDropPlanner.plan(S, chase(3, 1, true, 4, 138, 48, 0, 0, 0), spell(45, 0, 0, false, false),
		                                  cold(4), 4).reason());
		assertEquals(AutoDropPlanner.REASON_LOGOUT,
		             AutoDropPlanner.plan(S, chase(2, 2, false, 2, 43, 33, 1, 0, 0), spell(20, 0, 0, false, false),
		                                  cold(2), 2).reason());
		assertEquals(AutoDropPlanner.REASON_KNOWN_FACE,
		             AutoDropPlanner.plan(S, chase(2, 2, false, 2, 51.4, 41.4, 0, 0, 0),
		                                  spell(23.4, 0, 0, false, false), habit(0.213, 2), 2).reason());
		assertEquals(AutoDropPlanner.REASON_NONE,
		             AutoDropPlanner.plan(S, chase(0, 0, false, 3, 30, 30, 0, 0, 0), spell(30, 0, 0, false, false),
		                                  cold(3), 3).reason());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Rule 1: the lock.
	// ---------------------------------------------------------------------------------------------------------------

	@ParameterizedTest(name = "{0}")
	@MethodSource("rampageTriggers")
	@DisplayName("each rampage trigger alone locks a small chase to one star")
	void rampageTriggerAlone(String name, ChaseView chase) {
		DropPlan plan = AutoDropPlanner.plan(S, chase, spell(20, 0, 0, false, false), cold(chase.peak()), 2);

		assertEquals(1, plan.stars());
		assertEquals(STILL_HOT, plan.ending());
		assertEquals(AutoDropPlanner.REASON_RAMPAGE, plan.reason());
	}

	static Stream<Arguments> rampageTriggers() {
		return Stream.of(Arguments.of("4 opening crimes", chase(4, 4, false, 2, 60, 10, 0, 0, 0)),
		                 Arguments.of("peak 4", chase(1, 1, false, 4, 60, 10, 0, 0, 0)),
		                 Arguments.of("cop killed", chase(1, 1, true, 2, 60, 10, 0, 0, 0)));
	}

	@Test
	@DisplayName("3 opening crimes and peak 3 are not a rampage")
	void justBelowRampage() {
		DropPlan plan = AutoDropPlanner.plan(S, chase(3, 3, false, 3, 60, 10, 0, 0, 0),
		                                     spell(30, 0, 0, false, false), cold(3), 3);

		assertEquals(HUNKER_DOWN, plan.ending());
	}

	@Test
	@DisplayName("the lock lifts exactly when quiet reaches Lock_Cool_Seconds")
	void lockLiftsAtLockCoolSeconds() {
		SpellView spell = spell(45, 0, 0, false, false);
		ChaseView still = new ChaseView(3, 1, true, 4, 200_000, 179_999, 0, 0, 0);
		ChaseView cooled = new ChaseView(3, 1, true, 4, 200_000, 180_000, 0, 0, 0);

		assertEquals(STILL_HOT, AutoDropPlanner.plan(S, still, spell, cold(4), 4).ending());
		assertEquals(HUNKER_DOWN, AutoDropPlanner.plan(S, cooled, spell, cold(4), 4).ending());
		assertEquals(1.0, AutoDropPlanner.factor(S, still, spell(45, 0, 2, false, false), cold(4)), 0.001);
		assertEquals(0.5625, AutoDropPlanner.factor(S, cooled, spell(45, 0, 2, false, false), cold(4)), 0.001);
	}

	@Test
	@DisplayName("a logout locks a petty chase until it cools, then PETTY pays out")
	void logoutLock() {
		SpellView spell = spell(20, 0, 0, false, false);

		DropPlan hot = AutoDropPlanner.plan(S, chase(2, 2, false, 2, 60, 30, 1, 0, 0), spell, cold(2), 2);
		assertEquals(STILL_HOT, hot.ending());
		assertEquals(AutoDropPlanner.REASON_LOGOUT, hot.reason());
		assertEquals(1, hot.stars());

		assertEquals(PETTY, AutoDropPlanner.plan(S, chase(2, 2, false, 2, 300, 180, 1, 0, 0), spell, cold(2), 2)
		                                   .ending());
	}

	@Test
	@DisplayName("a rampage that also logged out reports the rampage")
	void rampageReasonWinsOverLogout() {
		DropPlan plan = AutoDropPlanner.plan(S, chase(1, 1, true, 2, 60, 10, 2, 0, 0), spell(20, 0, 0, false, false),
		                                     cold(2), 2);

		assertEquals(AutoDropPlanner.REASON_RAMPAGE, plan.reason());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Rule 2: PETTY.
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a chase with no crime is never petty")
	void zeroCrimesNeverPetty() {
		DropPlan plan = AutoDropPlanner.plan(S, chase(0, 0, false, 1, 20, 20, 0, 0, 0), spell(10, 0, 0, false, false),
		                                     cold(1), 1);

		assertEquals(HUNKER_DOWN, plan.ending());
	}

	@Test
	@DisplayName("3 crimes falls through PETTY")
	void threeCrimesFallThrough() {
		DropPlan plan = AutoDropPlanner.plan(S, chase(3, 3, false, 2, 60, 30, 0, 0, 0), spell(20, 0, 0, false, false),
		                                     cold(2), 2);

		assertEquals(HUNKER_DOWN, plan.ending());
		assertEquals(1, plan.stars());
	}

	@Test
	@DisplayName("1 crime at peak 1 is petty: the single star goes")
	void oneCrimePetty() {
		DropPlan plan = AutoDropPlanner.plan(S, chase(1, 1, false, 1, 20, 20, 0, 0, 0), spell(10, 0, 0, false, false),
		                                     cold(1), 1);

		assertEquals(PETTY, plan.ending());
		assertEquals(1, plan.stars());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Rule order.
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("PETTY beats COLD_TRAIL, STILL_HOT beats both")
	void ruleOrder() {
		SpellView outside = spell(5, 20, 0, false, false);
		// Petty and long-quiet at once: chase 200 s >= 2 x 60 s, quiet 100 s >= 90 s, and outside the zone.
		ChaseView pettyAndCold = chase(2, 2, false, 2, 200, 100, 0, 0, 0);
		assertEquals(PETTY, AutoDropPlanner.plan(S, pettyAndCold, outside, cold(2), 2).ending());

		// The same chase after a logout, still under the 180 s lock.
		ChaseView locked = chase(2, 2, false, 2, 200, 100, 1, 0, 0);
		assertEquals(STILL_HOT, AutoDropPlanner.plan(S, locked, outside, cold(2), 2).ending());

		// A rampage that is cold-trail long: still locked.
		ChaseView rampageCold = chase(5, 4, false, 3, 400, 100, 0, 0, 0);
		assertEquals(STILL_HOT, AutoDropPlanner.plan(S, rampageCold, outside, cold(3), 3).ending());

		// COLD_TRAIL beats CLEAN_BREAK.
		ChaseView coldChase = chase(3, 1, false, 3, 200, 100, 0, 0, 0);
		assertEquals(COLD_TRAIL, AutoDropPlanner.plan(S, coldChase, outside, cold(3), 3).ending());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Rule 3: COLD_TRAIL.
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("COLD_TRAIL fires exactly at Ratio x T and at Quiet_Seconds")
	void coldTrailBoundaries() {
		SpellView spell = spell(30, 0, 0, false, false);
		// Peak 3 on a cold server: T = 90, line = 180 s.
		assertEquals(COLD_TRAIL, plan(new ChaseView(3, 1, false, 3, 180_000, 90_000, 0, 0, 0), spell, cold(3)));
		assertEquals(HUNKER_DOWN, plan(new ChaseView(3, 1, false, 3, 179_999, 90_000, 0, 0, 0), spell, cold(3)));
		assertEquals(HUNKER_DOWN, plan(new ChaseView(3, 1, false, 3, 180_000, 89_999, 0, 0, 0), spell, cold(3)));

		// A learned T of 76.7 s moves the line to 153.4 s (W1).
		Learned learned = new Learned(0, 76.7);
		assertEquals(COLD_TRAIL, plan(new ChaseView(3, 1, false, 3, 153_400, 90_000, 0, 0, 0), spell, learned));
		assertEquals(HUNKER_DOWN, plan(new ChaseView(3, 1, false, 3, 153_399, 90_000, 0, 0, 0), spell, learned));
	}

	@Test
	@DisplayName("COLD_TRAIL drops every star")
	void coldTrailDropsAll() {
		DropPlan plan = AutoDropPlanner.plan(S, chase(3, 1, false, 5, 400, 200, 0, 0, 0),
		                                     spell(60, 0, 0, false, false), cold(5), 4);

		assertEquals(4, plan.stars());
	}

	private static Ending plan(ChaseView chase, SpellView spell, Learned learned) {
		return AutoDropPlanner.plan(S, chase, spell, learned, 3).ending();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Rule 4: CLEAN_BREAK.
	// ---------------------------------------------------------------------------------------------------------------

	@ParameterizedTest(name = "level {0}, fraction {1} -> {2}")
	@CsvSource({"1, 0.5, 1", "2, 0.5, 1", "3, 0.5, 2", "4, 0.5, 2", "5, 0.5, 3",
	            "1, 0.0, 1", "2, 0.0, 1", "3, 0.0, 1", "4, 0.0, 1", "5, 0.0, 1",
	            "1, 1.0, 1", "2, 1.0, 2", "3, 1.0, 3", "4, 1.0, 4", "5, 1.0, 5"})
	@DisplayName("CLEAN_BREAK rounds level x Drop_Fraction up, at least one star")
	void cleanBreakCeil(int level, double fraction, int stars) {
		DropPlan plan = AutoDropPlanner.plan(withCleanBreakFraction(fraction), chase(3, 1, false, 3, 60, 30, 0, 0, 0),
		                                     spell(5, 10, 0, false, false), cold(3), level);

		assertEquals(CLEAN_BREAK, plan.ending());
		assertEquals(stars, plan.stars());
	}

	@Test
	@DisplayName("CLEAN_BREAK needs Outside_Ratio of the search time outside")
	void cleanBreakRatioBoundary() {
		ChaseView chase = chase(3, 1, false, 3, 60, 30, 0, 0, 0);

		assertEquals(CLEAN_BREAK, AutoDropPlanner.plan(S, chase, spell(10, 10, 0, false, false), cold(3), 3).ending());
		assertEquals(HUNKER_DOWN, AutoDropPlanner.plan(S, chase, new SpellView(10_001, 10_000, 0, false, false),
		                                               cold(3), 3).ending());
		assertEquals(HUNKER_DOWN, AutoDropPlanner.plan(S, chase, spell(0, 0, 0, false, false), cold(3), 3).ending());
	}

	@Test
	@DisplayName("teleported blocks CLEAN_BREAK only")
	void teleportBlocksCleanBreakOnly() {
		SpellView teleported = spell(5, 20, 0, false, true);

		assertEquals(HUNKER_DOWN,
		             AutoDropPlanner.plan(S, chase(3, 1, false, 3, 60, 30, 0, 0, 0), teleported, cold(3), 3).ending());
		assertEquals(PETTY,
		             AutoDropPlanner.plan(S, chase(2, 2, false, 2, 60, 30, 0, 0, 0), teleported, cold(2), 2).ending());
		assertEquals(COLD_TRAIL,
		             AutoDropPlanner.plan(S, chase(3, 1, false, 3, 200, 100, 0, 0, 0), teleported, cold(3), 3)
		                            .ending());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Respot_Limit and the known face.
	// ---------------------------------------------------------------------------------------------------------------

	@ParameterizedTest(name = "{0}")
	@MethodSource("lumpBlockers")
	@DisplayName("Respot_Limit and knownFace switch off PETTY and CLEAN_BREAK but not COLD_TRAIL")
	void lumpBlockers(String name, int respots, int recentEnds, double delta) {
		SpellView outside = spell(5, 20, 0, false, false);
		Learned learned = new Learned(delta, S.typicalFor(2));

		assertEquals(HUNKER_DOWN,
		             AutoDropPlanner.plan(S, chase(2, 2, false, 2, 60, 30, 0, respots, recentEnds),
		                                  spell(20, 0, 0, false, false), learned, 2).ending(), "petty");
		assertEquals(HUNKER_DOWN,
		             AutoDropPlanner.plan(S, chase(3, 1, false, 2, 60, 30, 0, respots, recentEnds), outside, learned,
		                                  2).ending(), "clean break");
		assertEquals(COLD_TRAIL,
		             AutoDropPlanner.plan(S, chase(3, 1, false, 2, 200, 100, 0, respots, recentEnds), outside,
		                                  learned, 2).ending(), "cold trail");
	}

	static Stream<Arguments> lumpBlockers() {
		return Stream.of(Arguments.of("respots 5 > Respot_Limit 4", 5, 0, 0.0),
		                 Arguments.of("repeat offender (3 recent ends)", 0, 3, 0.0),
		                 Arguments.of("habitual escaper (delta 0.20)", 0, 0, 0.20));
	}

	@Test
	@DisplayName("respots at the limit, 2 recent ends and delta just under 0.20 still pay out")
	void lumpBlockersJustBelow() {
		ChaseView chase = chase(2, 2, false, 2, 60, 30, 0, 4, 2);

		assertEquals(PETTY, AutoDropPlanner.plan(S, chase, spell(20, 0, 0, false, false),
		                                         new Learned(0.199, S.typicalFor(2)), 2).ending());
	}

	@Test
	@DisplayName("Repeat_Chases 0 turns the repeat-offender rule off")
	void repeatChasesZeroIsOff() {
		DropPlan plan = AutoDropPlanner.plan(withRepeatChases(0), chase(2, 2, false, 2, 60, 30, 0, 0, 50),
		                                     spell(20, 0, 0, false, false), cold(2), 2);

		assertEquals(PETTY, plan.ending());
	}

	@Test
	@DisplayName("HUNKER_DOWN reason: known face first, then narrow, then none")
	void hunkerReasons() {
		ChaseView plain = chase(3, 1, false, 3, 60, 30, 0, 0, 0);
		ChaseView repeat = chase(3, 1, false, 3, 60, 30, 0, 0, 3);

		assertEquals(AutoDropPlanner.REASON_KNOWN_FACE,
		             AutoDropPlanner.plan(S, repeat, spell(30, 0, 0, true, false), cold(3), 3).reason());
		assertEquals(AutoDropPlanner.REASON_NARROW,
		             AutoDropPlanner.plan(S, plain, spell(30, 0, 0, true, false), cold(3), 3).reason());
		assertEquals(AutoDropPlanner.REASON_NONE,
		             AutoDropPlanner.plan(S, plain, spell(30, 0, 0, false, false), cold(3), 3).reason());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Stars are always 1..level.
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("stars are never 0 and never above the level")
	void starsWithinOneAndLevel() {
		for (int level = 1; level <= 5; level++) {
			for (int peak = level; peak <= 6; peak++) {
				for (int crimes = 0; crimes <= 5; crimes++) {
					for (double fraction : new double[]{0.0, 0.3, 0.5, 1.0}) {
						for (boolean outside : new boolean[]{false, true}) {
							for (double chaseS : new double[]{10, 1000}) {
								ChaseView chase = chase(crimes, Math.min(crimes, 1), false, peak, chaseS, chaseS / 2,
								                        0, 0, 0);
								SpellView spell = spell(outside ? 0 : 10, outside ? 10 : 0, 0, false, false);
								int stars = AutoDropPlanner.plan(withCleanBreakFraction(fraction), chase, spell,
								                                 cold(peak), level).stars();

								assertTrue(stars >= 1 && stars <= level,
								           "level " + level + " peak " + peak + " crimes " + crimes + " fraction " +
								           fraction + " outside " + outside + " chase " + chaseS + ": " + stars);
							}
						}
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Section 3.4: the timer factor.
	// ---------------------------------------------------------------------------------------------------------------

	@ParameterizedTest(name = "steps {0} -> {1}")
	@CsvSource({"0, 1.0", "1, 0.75", "2, 0.5625", "3, 0.421875", "4, 0.4", "10, 0.4"})
	@DisplayName("momentum is Step_Speed to the power of steps, never below Floor")
	void momentumPowerAndFloor(int steps, double factor) {
		assertEquals(factor, AutoDropPlanner.factor(S, chase(3, 1, false, 3, 60, 30, 0, 0, 0),
		                                            spell(30, 0, steps, false, false), cold(3)), 0.001);
	}

	@Test
	@DisplayName("narrow uses Narrow_Step_Speed when not locked")
	void narrowStep() {
		assertEquals(0.5, AutoDropPlanner.factor(S, chase(3, 1, false, 3, 60, 30, 0, 0, 0),
		                                         spell(30, 0, 1, true, false), cold(3)), 0.001);
	}

	@Test
	@DisplayName("narrow gives no speed-up under a lock")
	void narrowNoSpeedUpUnderLock() {
		assertEquals(1.0, AutoDropPlanner.factor(S, chase(3, 1, true, 4, 60, 30, 0, 0, 0),
		                                         spell(30, 0, 3, true, false), cold(4)), 0.001);
		assertEquals(1.0, AutoDropPlanner.factor(S, chase(2, 2, false, 2, 60, 30, 1, 0, 0),
		                                         spell(30, 0, 3, true, false), cold(2)), 0.001);
	}

	@ParameterizedTest(name = "delta {0} -> {1}")
	@CsvSource({"1.0, 1.6", "0.5, 1.4", "0.0, 1.0", "-0.25, 0.8", "-1.0, 0.6"})
	@DisplayName("the habit clamps to Min_Time_Factor..Max_Time_Factor")
	void habitClampsBothEnds(double delta, double factor) {
		assertEquals(factor, AutoDropPlanner.factor(S, chase(3, 1, false, 3, 60, 30, 0, 0, 0),
		                                            spell(30, 0, 0, false, false), new Learned(delta, 90)), 0.001);
	}

	@Test
	@DisplayName("Floor bounds momentum and habit together; Max_Time_Factor bounds the stretch")
	void floorBoundsHabitAndMomentum() {
		ChaseView chase = chase(3, 1, false, 3, 60, 30, 0, 0, 0);

		// 0.75^2 x 0.6 = 0.3375 -> Floor 0.4.
		assertEquals(0.4, AutoDropPlanner.factor(S, chase, spell(30, 0, 2, false, false), new Learned(-1, 90)), 0.001);
		// Locked at habit 1.6: 1.0 x 1.6.
		assertEquals(1.6, AutoDropPlanner.factor(S, chase(3, 1, true, 4, 60, 30, 0, 0, 0),
		                                         spell(30, 0, 2, false, false), new Learned(1, 120)), 0.001);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// The cold-start guarantee.
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("invariant: cold, no lump rule -> 1 star and factor max(0.4, 0.75^steps), 1.0 when locked")
	void coldStartInvariant() {
		for (int level = 1; level <= 5; level++) {
			for (int steps = 0; steps <= 8; steps++) {
				for (boolean locked : new boolean[]{false, true}) {
					// 3 crimes (never petty), short chase (never cold trail), inside the zone (never clean break).
					ChaseView chase = chase(3, 1, false, level, 20, locked ? 10 : 200, locked ? 1 : 0, 0, 0);
					SpellView spell = spell(15, 0, steps, false, false);
					Learned learned = cold(level);

					DropPlan plan = AutoDropPlanner.plan(S, chase, spell, learned, level);
					double expected = locked ? 1.0 : Math.max(0.4, Math.pow(0.75, steps));

					assertEquals(1, plan.stars(), "level " + level + " steps " + steps + " locked " + locked);
					assertEquals(locked ? STILL_HOT : HUNKER_DOWN, plan.ending());
					assertEquals(expected, AutoDropPlanner.factor(S, chase, spell, learned), 1e-9,
					             "level " + level + " steps " + steps + " locked " + locked);
				}
			}
		}
	}
}
