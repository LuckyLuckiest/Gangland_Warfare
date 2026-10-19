package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.crime.Crimes;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("ChaseArcs")
class ChaseArcsTest {

	private static final long MIN = 60_000L;

	private final UUID         id       = UUID.randomUUID();
	private final AutoSettings settings = AutoSettings.DEFAULT;

	private AtomicLong clock;
	private ChaseArcs  arcs;

	@BeforeEach
	void setUp() {
		clock = new AtomicLong(1_000_000L);
		arcs  = new ChaseArcs(clock::get);
	}

	private AutoDrop.ChaseView view() {
		return arcs.view(id, List.of(), settings);
	}

	private CrimeRecord crime(String crimeId, long at) {
		return new CrimeRecord(crimeId, 10, at, mock(Location.class));
	}

	@Test
	@DisplayName("start at level 4 seeds peak 4 and lastHotAt = now, so an admin /wanted add 4 chase is a rampage")
	void start_atLevelFour_seedsPeakAndQuiet() {
		arcs.start(id, WantedCause.ADMIN, 4);

		assertEquals(4, view().peak());
		assertEquals(0, view().quietMs());
		assertEquals(0, view().quits());
	}

	@Test
	@DisplayName("peak only rises; hot resets quiet")
	void peakAndHot() {
		arcs.start(id, WantedCause.CRIME, 1);
		clock.addAndGet(10_000);
		arcs.peak(id, 3);
		arcs.peak(id, 2);
		assertEquals(3, view().peak());
		assertEquals(10_000, view().quietMs());

		arcs.hot(id);
		assertEquals(0, view().quietMs());
		assertEquals(10_000, view().chaseMs());
	}

	@Test
	@DisplayName("seen counts a respot only after searchStarted, and once per loss")
	void seen_countsRespotOnlyAfterASearch() {
		arcs.start(id, WantedCause.CRIME, 2);

		arcs.seen(id);
		assertEquals(0, view().respots());

		arcs.searchStarted(id);
		arcs.seen(id);
		arcs.seen(id);
		assertEquals(1, view().respots());

		arcs.searchStarted(id);
		arcs.seen(id);
		assertEquals(2, view().respots());
	}

	@Test
	@DisplayName("a quit then a restore shifts the stamps by the offline gap, so chase and quiet exclude it")
	void restore_excludesTheOfflineGap() {
		arcs.start(id, WantedCause.CRIME, 2);
		clock.addAndGet(20_000);
		arcs.lost(id);
		clock.addAndGet(5_000);
		arcs.quit(id);
		assertEquals(1, view().quits());

		clock.addAndGet(10 * MIN);
		arcs.restore(id);
		clock.addAndGet(3_000);

		assertEquals(28_000, view().chaseMs());
		assertEquals(28_000, view().quietMs());
		assertEquals(0, arcs.arc(id).offlineAt());
		assertEquals(arcs.arc(id).startedAt() + 20_000, arcs.arc(id).lastLostAt());
	}

	@Test
	@DisplayName("while offline the chase clock stands still at the quit")
	void view_whileOffline_usesTheQuitTime() {
		arcs.start(id, WantedCause.CRIME, 2);
		clock.addAndGet(5_000);
		arcs.quit(id);
		clock.addAndGet(5 * MIN);

		assertEquals(5_000, view().chaseMs());
	}

	@Test
	@DisplayName("a RESTORE start with no arc gives quits = 1 and peak = level")
	void restoreStartWithNoArc_countsOneQuit() {
		arcs.start(id, WantedCause.RESTORE, 3);

		assertEquals(1, view().quits());
		assertEquals(3, view().peak());
	}

	@Test
	@DisplayName("end appends to recent only when the chase had a crime; an admin chase leaves no trace")
	void end_appendsOnlyWithACrime() {
		arcs.start(id, WantedCause.ADMIN, 2);
		arcs.end(id, false);
		assertFalse(arcs.has(id));
		assertEquals(0, arcs.recentEnds(id, 30));

		arcs.start(id, WantedCause.CRIME, 2);
		arcs.end(id, true);
		assertEquals(1, arcs.recentEnds(id, 30));
	}

	@Test
	@DisplayName("recent is capped at 8 and aged by the window")
	void recent_isCappedAndAged() {
		for (int i = 0; i < 12; i++) {
			arcs.start(id, WantedCause.CRIME, 1);
			arcs.end(id, true);
			clock.addAndGet(1_000);
		}
		assertEquals(8, arcs.recentEnds(id, 30));

		clock.addAndGet(30 * MIN - 6_000);
		int aged = arcs.recentEnds(id, 30);
		assertTrue(aged > 0 && aged < 8);

		clock.addAndGet(2 * MIN);
		assertEquals(0, arcs.recentEnds(id, 30));
	}

	@Test
	@DisplayName("prune drops arcs offline over 30 minutes; the player returns as a RESTORE start")
	void prune_dropsLongOfflineArcs() {
		arcs.start(id, WantedCause.CRIME, 2);
		arcs.quit(id);

		clock.addAndGet(29 * MIN);
		arcs.prune();
		assertTrue(arcs.has(id));

		clock.addAndGet(2 * MIN);
		arcs.prune();
		assertFalse(arcs.has(id));

		arcs.start(id, WantedCause.RESTORE, 2);
		assertEquals(1, view().quits());
	}

	@Test
	@DisplayName("view counts crimes, the opening window and a cop kill; no arc gives null")
	void view_readsTheCrimeList() {
		assertNull(arcs.view(id, List.of(), settings));

		arcs.start(id, WantedCause.CRIME, 2);
		long t = clock.get();
		List<CrimeRecord> crimes = List.of(crime(Crimes.ASSAULT_COP, t), crime(Crimes.ASSAULT_COP, t + 29_000),
		                                   crime(Crimes.KILL_COP, t + 31_000));
		AutoDrop.ChaseView v = arcs.view(id, crimes, settings);

		assertEquals(3, v.crimes());
		assertEquals(2, v.opening());
		assertTrue(v.copKilled());
	}

	@Test
	@DisplayName("a stale sub-threshold crime from long before the chase is not on it; a build-up crime just before is")
	void view_ignoresStalePreChaseCrimes() {
		long t = clock.get();
		CrimeRecord stale   = crime(Crimes.KILL_PLAYER, t - 120 * MIN);
		CrimeRecord buildUp = crime(Crimes.KILL_PLAYER, t - 10_000);
		arcs.start(id, WantedCause.CRIME, 2);
		List<CrimeRecord> crimes = List.of(stale, buildUp, crime(Crimes.KILL_PLAYER, t),
		                                   crime(Crimes.KILL_PLAYER, t + 15_000));

		AutoDrop.ChaseView v = arcs.view(id, crimes, settings);

		assertEquals(3, v.crimes());
		assertEquals(3, v.opening());
	}

	@Test
	@DisplayName("Opening_Seconds 0: the crime that raised the first star, stamped a few ms before the start, is on the chase")
	void view_openingZero_keepsTheTriggeringCrime() {
		AutoSettings d = AutoSettings.DEFAULT;
		AutoSettings zero = new AutoSettings(0, d.rampageCrimes(), d.rampagePeakLevel(), d.lockCoolSeconds(),
		                                     d.respotLimit(), d.petty(), d.coldTrail(), d.cleanBreak(), d.momentum(),
		                                     d.repeatChases(), d.repeatWindowMinutes(), d.learning());
		List<CrimeRecord> crimes = List.of(crime(Crimes.KILL_COP, clock.get()));
		clock.addAndGet(5);
		arcs.start(id, WantedCause.CRIME, 1);

		AutoDrop.ChaseView v = arcs.view(id, crimes, zero);

		assertEquals(1, v.crimes());
		assertTrue(v.copKilled());
		assertTrue(arcs.hadCrime(id, crimes, zero));
	}

	@Test
	@DisplayName("a restore does not move the crime cut-off: crimes keep their own unshifted stamps")
	void view_afterRestore_keepsTheChasesCrimes() {
		long t = clock.get();
		arcs.start(id, WantedCause.CRIME, 2);
		List<CrimeRecord> crimes = List.of(crime(Crimes.KILL_PLAYER, t));
		arcs.quit(id);
		clock.addAndGet(20 * MIN);
		arcs.restore(id);

		assertEquals(1, arcs.view(id, crimes, settings).crimes());
	}

	@Test
	@DisplayName("pending plan is taken once")
	void pending_isTakenOnce() {
		arcs.start(id, WantedCause.CRIME, 2);
		AutoDrop.DropPlan plan = new AutoDrop.DropPlan(2, AutoDrop.Ending.PETTY, "small");
		arcs.stashPending(id, plan);

		assertSame(plan, arcs.takePending(id));
		assertNull(arcs.takePending(id));
	}

	private static final String BRANDISH = "Brandish_Near_Cop";

	private AutoDrop.ChaseView weighted(List<CrimeRecord> crimes) {
		return arcs.view(id, crimes, settings, HeatSettings.DEFAULT::weightOf);
	}

	@Test
	@DisplayName("four Brandish_Near_Cop records inside the opening are not a rampage: opening 0")
	void fourBrandishCrimesInTheOpening_areNotARampage() {
		long t = clock.get();
		arcs.start(id, WantedCause.CRIME, 1);
		List<CrimeRecord> crimes = List.of(crime(BRANDISH, t), crime(BRANDISH, t + 5_000),
		                                   crime(BRANDISH, t + 10_000), crime(BRANDISH, t + 15_000));

		AutoDrop.ChaseView v = weighted(crimes);

		assertEquals(0, v.opening());
		assertEquals(4, v.crimes());
		AutoDrop.DropPlan plan = AutoDropPlanner.plan(settings, v, new AutoDrop.SpellView(0, 0, 0, false, false),
		                                              AutoDrop.Learned.cold(settings, 1), 1);
		assertFalse(AutoDropPlanner.REASON_RAMPAGE.equals(plan.reason()));
	}

	@Test
	@DisplayName("one Kill_Cop is still a rampage")
	void oneKillCop_isStillARampage() {
		arcs.start(id, WantedCause.CRIME, 1);

		AutoDrop.ChaseView v = weighted(List.of(crime(Crimes.KILL_COP, clock.get())));

		assertTrue(v.copKilled());
		assertEquals(1, v.opening());
	}

	@Test
	@DisplayName("four Kill_Player crimes still count as the opening")
	void fourKillPlayerCrimes_stillCountAsTheOpening() {
		long t = clock.get();
		arcs.start(id, WantedCause.CRIME, 1);

		AutoDrop.ChaseView v = weighted(List.of(crime(Crimes.KILL_PLAYER, t), crime(Crimes.KILL_PLAYER, t + 5_000),
		                                        crime(Crimes.KILL_PLAYER, t + 10_000),
		                                        crime(Crimes.KILL_PLAYER, t + 15_000)));

		assertEquals(4, v.opening());
	}

	@Test
	@DisplayName("a cheap crime first: the opening window starts at the first heavy crime")
	void cheapCrimeFirst_windowStartsAtTheFirstHeavyCrime() {
		long t = clock.get();
		arcs.start(id, WantedCause.CRIME, 1);

		AutoDrop.ChaseView v = weighted(List.of(crime(BRANDISH, t), crime(Crimes.KILL_PLAYER, t + 20_000),
		                                        crime(Crimes.KILL_PLAYER, t + 30_000),
		                                        crime(Crimes.KILL_PLAYER, t + 40_000),
		                                        crime(Crimes.KILL_PLAYER, t + 45_000)));

		assertEquals(4, v.opening());
		assertEquals(5, v.crimes());
	}

	@Test
	@DisplayName("a heavy crime from before the chase is dropped by the on-chase prefilter and is not the window start")
	void preChaseHeavyCrime_isNotTheWindowStart() {
		long t = clock.get();
		arcs.start(id, WantedCause.CRIME, 1);

		AutoDrop.ChaseView v = weighted(List.of(crime(Crimes.KILL_PLAYER, t - 10 * MIN), crime(Crimes.KILL_PLAYER, t),
		                                        crime(Crimes.KILL_PLAYER, t + 10_000),
		                                        crime(Crimes.KILL_PLAYER, t + 20_000)));

		assertEquals(3, v.opening());
		assertEquals(3, v.crimes());
	}

	@Test
	@DisplayName("characterization pin: the 3-argument view counts every crime, whatever its weight")
	void threeArgView_countsEveryCrime() {
		long t = clock.get();
		arcs.start(id, WantedCause.CRIME, 1);

		AutoDrop.ChaseView v = arcs.view(id, List.of(crime(BRANDISH, t), crime(BRANDISH, t + 5_000),
		                                             crime(BRANDISH, t + 10_000), crime(BRANDISH, t + 15_000)),
		                                 settings);

		assertEquals(4, v.opening());
	}

	@Test
	@DisplayName("offlineTotalMs sums every restored gap; 0 with no arc or no restore")
	void offlineTotalMs_sumsRestoredGaps() {
		assertEquals(0, arcs.offlineTotalMs(id));
		arcs.start(id, WantedCause.CRIME, 2);
		assertEquals(0, arcs.offlineTotalMs(id));

		arcs.quit(id);
		clock.addAndGet(4 * MIN);
		arcs.restore(id);
		clock.addAndGet(10_000);
		arcs.quit(id);
		clock.addAndGet(2 * MIN);
		arcs.restore(id);

		assertEquals(6 * MIN, arcs.offlineTotalMs(id));
		assertEquals(6 * MIN, arcs.arc(id).offlineTotal());
	}

	@Test
	@DisplayName("copDown, bust and markNews count on the arc; copDown and bust add up, markNews sets the flag")
	void reportCounters_countOnTheArc() {
		arcs.start(id, WantedCause.CRIME, 3);

		arcs.copDown(id);
		arcs.copDown(id);
		arcs.bust(id);
		arcs.markNews(id);

		assertEquals(2, arcs.arc(id).copsDown());
		assertEquals(1, arcs.arc(id).busts());
		assertTrue(arcs.arc(id).newsSent());
	}

	@Test
	@DisplayName("a fresh arc has zero counters and no news")
	void reportCounters_startAtZero() {
		arcs.start(id, WantedCause.CRIME, 1);

		assertEquals(0, arcs.arc(id).copsDown());
		assertEquals(0, arcs.arc(id).busts());
		assertFalse(arcs.arc(id).newsSent());
	}

	@Test
	@DisplayName("copDown, bust and markNews without an arc are no-ops")
	void reportCounters_withoutArc_noOp() {
		arcs.copDown(id);
		arcs.bust(id);
		arcs.markNews(id);

		assertFalse(arcs.has(id));
	}

	@Test
	@DisplayName("a quit and restore keeps the counters and the news flag")
	void reportCounters_surviveRestore() {
		arcs.start(id, WantedCause.CRIME, 4);
		arcs.copDown(id);
		arcs.bust(id);
		arcs.markNews(id);

		arcs.quit(id);
		clock.addAndGet(MIN);
		arcs.restore(id);

		assertEquals(1, arcs.arc(id).copsDown());
		assertEquals(1, arcs.arc(id).busts());
		assertTrue(arcs.arc(id).newsSent());
	}

	@Test
	@DisplayName("a new chase after end starts its counters from zero")
	void reportCounters_resetOnNewChase() {
		arcs.start(id, WantedCause.CRIME, 4);
		arcs.copDown(id);
		arcs.markNews(id);
		arcs.end(id, false);

		arcs.start(id, WantedCause.CRIME, 1);

		assertEquals(0, arcs.arc(id).copsDown());
		assertFalse(arcs.arc(id).newsSent());
	}

	// ---- 0.21.0 wave 2 (fnd-w2): earned stars and the sighted flag ----

	@Test
	@DisplayName("0.21.0: a CRIME start seeds earned and earnedPeak with the level; any other start earns 0")
	void earned_crimeStartSeedsLevel() {
		arcs.start(id, WantedCause.CRIME, 2);
		assertEquals(2, arcs.arc(id).earned());
		assertEquals(2, arcs.arc(id).earnedPeak());
		assertEquals(clock.get(), arcs.arc(id).earnedAt());

		UUID other = UUID.randomUUID();
		arcs.start(other, WantedCause.SIGN, 3);
		assertEquals(0, arcs.arc(other).earned());
		assertEquals(0, arcs.arc(other).earnedPeak());
		assertEquals(0, arcs.arc(other).earnedAt());
	}

	@Test
	@DisplayName("0.21.0: sign stars below a crime raise do not count as earned")
	void earned_signStarsBelowCrimeDoNotCount() {
		arcs.start(id, WantedCause.SIGN, 4);

		arcs.earn(id, 4, 5, WantedCause.CRIME);

		assertEquals(1, arcs.arc(id).earned());
		assertEquals(1, arcs.arc(id).earnedPeak());
	}

	@Test
	@DisplayName("0.21.0: a drop clamps earned, earnedPeak stays, earnedAt is unchanged")
	void earned_dropClampsPeakStays() {
		arcs.start(id, WantedCause.CRIME, 3);
		long at = arcs.arc(id).earnedAt();
		clock.addAndGet(5_000);

		arcs.earn(id, 3, 1, WantedCause.EVASION);

		assertEquals(1, arcs.arc(id).earned());
		assertEquals(3, arcs.arc(id).earnedPeak());
		assertEquals(at, arcs.arc(id).earnedAt());
	}

	@Test
	@DisplayName("0.21.0: an admin raise is ignored, a later crime raise counts")
	void earned_adminRaiseIgnored() {
		arcs.start(id, WantedCause.CRIME, 1);

		arcs.earn(id, 1, 4, WantedCause.ADMIN);
		assertEquals(1, arcs.arc(id).earned());

		arcs.earn(id, 4, 5, WantedCause.CRIME);
		assertEquals(2, arcs.arc(id).earned());
		assertEquals(2, arcs.arc(id).earnedPeak());
	}

	@Test
	@DisplayName("0.21.0: a UNKNOWN raise (PostEscapeSpotting re-spot) never earns; an UNKNOWN start earns 0 (D-W2-24)")
	void earned_unknownRaiseIgnored() {
		arcs.start(id, WantedCause.CRIME, 2);

		arcs.earn(id, 2, 3, WantedCause.UNKNOWN);

		assertEquals(2, arcs.arc(id).earned());
		assertEquals(2, arcs.arc(id).earnedPeak());

		UUID other = UUID.randomUUID();
		arcs.start(other, WantedCause.UNKNOWN, 1);
		assertEquals(0, arcs.arc(other).earned());
	}

	@Test
	@DisplayName("0.21.0: earned survives a quit and a restore; restore changes none of the three fields")
	void earned_survivesQuitRestore() {
		arcs.start(id, WantedCause.CRIME, 3);
		long at = arcs.arc(id).earnedAt();

		arcs.quit(id);
		clock.addAndGet(60_000);
		arcs.restore(id);

		assertEquals(3, arcs.arc(id).earned());
		assertEquals(3, arcs.arc(id).earnedPeak());
		assertEquals(at, arcs.arc(id).earnedAt());
	}

	@Test
	@DisplayName("0.21.0: earn on an unknown id does not throw and creates no arc")
	void earn_noArc_noop() {
		assertDoesNotThrow(() -> arcs.earn(id, 0, 3, WantedCause.CRIME));

		assertFalse(arcs.has(id));
	}

	@Test
	@DisplayName("0.21.0: sighted is set by seen() only, not by lost or searchStarted; respots count as before")
	void sighted_setBySeenOnly() {
		arcs.start(id, WantedCause.CRIME, 3);
		assertFalse(arcs.arc(id).sighted());

		arcs.lost(id);
		arcs.searchStarted(id);
		assertFalse(arcs.arc(id).sighted());

		arcs.seen(id);
		assertTrue(arcs.arc(id).sighted());
		assertEquals(1, view().respots());

		arcs.searchStarted(id);
		arcs.seen(id);
		assertTrue(arcs.arc(id).sighted());
		assertEquals(2, view().respots());
	}

	@Test
	@DisplayName("0.21.0: sighted survives quit and restore; a new arc starts false")
	void sighted_survivesQuitRestore_newArcFalse() {
		arcs.start(id, WantedCause.CRIME, 3);
		arcs.seen(id);

		arcs.quit(id);
		clock.addAndGet(1_000);
		arcs.restore(id);
		assertTrue(arcs.arc(id).sighted());

		arcs.end(id, true);
		arcs.start(id, WantedCause.RESTORE, 4);
		assertFalse(arcs.arc(id).sighted());
	}

	@Test
	@DisplayName("0.21.0: seen with no arc does not throw")
	void seen_noArc_noop() {
		assertDoesNotThrow(() -> arcs.seen(id));
		assertFalse(arcs.has(id));
	}

	@Test
	@DisplayName("0.21.0: a CRIME re-raise below the peak keeps earnedPeak and stamps earnedAt; an ADMIN raise leaves earnedAt")
	void earned_reRaiseBelowPeak_keepsPeak_stampsAt() {
		arcs.start(id, WantedCause.CRIME, 3);
		arcs.earn(id, 3, 1, WantedCause.EVASION);
		clock.addAndGet(5_000);

		arcs.earn(id, 1, 2, WantedCause.CRIME);

		assertEquals(2, arcs.arc(id).earned());
		assertEquals(3, arcs.arc(id).earnedPeak());
		assertEquals(clock.get(), arcs.arc(id).earnedAt());

		long at = arcs.arc(id).earnedAt();
		clock.addAndGet(5_000);
		arcs.earn(id, 2, 3, WantedCause.ADMIN);
		assertEquals(at, arcs.arc(id).earnedAt());
	}

	@Test
	@DisplayName("0.21.0 C-37: start with earns=false seeds nothing; earns=true seeds the level")
	void start_earnsFlag_seedsOrNot() {
		arcs.start(id, WantedCause.CRIME, 2, false);
		assertEquals(0, arcs.arc(id).earned());
		assertEquals(0, arcs.arc(id).earnedAt());
		assertEquals(WantedCause.CRIME, arcs.arc(id).startCause());

		arcs.start(id, WantedCause.SIGN, 2, true);
		assertEquals(2, arcs.arc(id).earned());
	}
}
