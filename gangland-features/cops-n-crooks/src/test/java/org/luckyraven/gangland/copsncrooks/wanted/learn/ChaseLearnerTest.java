package org.luckyraven.gangland.copsncrooks.wanted.learn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChargeSheetSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.DropMode;
import org.luckyraven.gangland.copsncrooks.wanted.config.EvasionSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.HudSettings;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ChaseLearner}: the learning rule of PLAN.md section 3.5 and the warm-server rows W1-W6 of section 3.6. The
 * repositories are mocks; the caches are read back through the data suppliers the learner hands them.
 */
@DisplayName("ChaseLearner")
class ChaseLearnerTest {

	private static final AutoSettings AUTO = AutoSettings.DEFAULT;
	private static final double       EPS  = 0.001;

	private ChaseConfigLoader                    loader;
	private IRepository<ChaseHabit>              habitRepo;
	private IRepository<ChaseLevelStat>          levelRepo;
	private Supplier<Collection<ChaseHabit>>     habits;
	private Supplier<Collection<ChaseLevelStat>> levels;
	private ChaseLearner                         learner;
	private long                                 now;

	private static ChaseConfig config(DropMode mode, boolean learning) {
		AutoSettings.Learning l    = AUTO.learning();
		AutoSettings          auto = new AutoSettings(AUTO.openingSeconds(), AUTO.rampageCrimes(),
		                                              AUTO.rampagePeakLevel(), AUTO.lockCoolSeconds(),
		                                              AUTO.respotLimit(), AUTO.petty(), AUTO.coldTrail(),
		                                              AUTO.cleanBreak(), AUTO.momentum(), AUTO.repeatChases(),
		                                              AUTO.repeatWindowMinutes(),
		                                              new AutoSettings.Learning(learning, l.escapeRate(),
		                                                                        l.priorChases(), l.decayPerChase(),
		                                                                        l.habitualEscaperDelta(),
		                                                                        l.habitTimeStrength(),
		                                                                        l.minTimeFactor(), l.maxTimeFactor(),
		                                                                        l.minChaseSeconds(),
		                                                                        l.minSecondsBetweenOutcomes(),
		                                                                        l.forgetAfterDays()));
		EvasionSettings e = EvasionSettings.DEFAULT;
		return new ChaseConfig(HeatSettings.DEFAULT,
		                       new EvasionSettings(e.enabled(), e.lostSightSeconds(), mode, e.searchRadius(),
		                                           e.secondsToDrop(), e.outsideZoneSpeed(), auto),
		                       HudSettings.DEFAULT, ChargeSheetSettings.DEFAULT);
	}

	@SuppressWarnings("unchecked")
	private static ChaseLearner newLearner(ChaseConfigLoader loader, IRepository<ChaseHabit> habitRepo,
	                                       IRepository<ChaseLevelStat> levelRepo) {
		return new ChaseLearner(loader, habitRepo, levelRepo);
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		loader    = mock(ChaseConfigLoader.class);
		habitRepo = mock(IRepository.class);
		levelRepo = mock(IRepository.class);
		configure(DropMode.AUTO, true);

		learner = newLearner(loader, habitRepo, levelRepo);

		ArgumentCaptor<Supplier<Collection<ChaseHabit>>>     habitCaptor = ArgumentCaptor.forClass(Supplier.class);
		ArgumentCaptor<Supplier<Collection<ChaseLevelStat>>> levelCaptor = ArgumentCaptor.forClass(Supplier.class);
		verify(habitRepo).setDataSupplier(habitCaptor.capture());
		verify(levelRepo).setDataSupplier(levelCaptor.capture());
		habits = habitCaptor.getValue();
		levels = levelCaptor.getValue();

		now = 1_000_000_000L;
	}

	private void configure(DropMode mode, boolean learning) {
		when(loader.get()).thenReturn(config(mode, learning));
	}

	/** One finished chase, 200 s after the previous one, so the rate limit never trips unless a test wants it. */
	private void chase(UUID player, WantedCause start, WantedCause end, int peak, long chaseS, double contactS) {
		now += 200_000L;
		learner.record(new ChaseRecord(player, start, end, peak, chaseS * 1000L, Math.round(contactS * 1000), now),
		               now);
	}

	private void escape(UUID player, int peak, double contactS) {
		chase(player, WantedCause.CRIME, WantedCause.EVASION, peak, 60, contactS);
	}

	private void arrest(UUID player, int peak) {
		chase(player, WantedCause.CRIME, WantedCause.ARREST, peak, 60, 40);
	}

	private ChaseLevelStat level(int peak) {
		return levels.get().stream().filter(s -> s.level() == peak).findFirst().orElseThrow();
	}

	private ChaseHabit habit(UUID player) {
		return habits.get().stream().filter(h -> h.player().equals(player)).findFirst().orElseThrow();
	}

	/** A busy server: {@code S[3]} holds 100 chases at the shipped 0.55 escape rate. */
	private void seedBusyLevel3() {
		learner.load(List.of(), List.of(new ChaseLevelStat(3, 100, 55, 0, 0, now)));
	}

	@Test
	@DisplayName("cold start: a stranger reads delta 0 and the configured Typical_Seconds")
	void coldStart() {
		assertEquals(0, learner.delta(UUID.randomUUID()), 0);
		for (int peak = 1; peak <= 5; peak++) {
			assertEquals(AUTO.typicalFor(peak), learner.typicalSeconds(peak, AUTO), 0);
		}
		assertTrue(habits.get().isEmpty());
		assertTrue(levels.get().isEmpty());
	}

	@Test
	@DisplayName("W1: 40 getaways at peak 3 with a 70 s contact time give T(3) = 76.7")
	void w1LearnedTypicalLength() {
		for (int i = 0; i < 40; i++) {
			escape(UUID.randomUUID(), 3, 70);
		}

		assertEquals(70, level(3).typicalSeconds(), EPS);
		assertEquals(40, level(3).typicalCount(), EPS);
		assertEquals(76.667, learner.typicalSeconds(3, AUTO), EPS);
	}

	@Test
	@DisplayName("W2: a habitual escaper on a busy server reaches delta 0.199 after 5 escapes and 0.213 after 6")
	void w2HabitualEscaperBusyServer() {
		seedBusyLevel3();
		UUID     player   = UUID.randomUUID();
		double[] expected = {0.075, 0.123, 0.157, 0.181, 0.199, 0.213};

		for (double want : expected) {
			escape(player, 3, 60);
			assertEquals(want, learner.delta(player), EPS);
		}
		assertTrue(learner.delta(player) >= AUTO.learning().habitualEscaperDelta(), "known face from the 6th");
	}

	@Test
	@DisplayName("W2: a lone tester (no other rows) tops out at delta 0.206")
	void w2LoneTesterTopsOut() {
		UUID   player = UUID.randomUUID();
		double top    = 0;

		for (int i = 0; i < 40; i++) {
			escape(player, 3, 60);
			top = Math.max(top, learner.delta(player));
		}
		assertEquals(0.206, top, EPS);
	}

	@Test
	@DisplayName("W3: arrested in his last 6 chases at peak 3 on a busy server gives delta -0.26")
	void w3HabitualLoser() {
		seedBusyLevel3();
		UUID player = UUID.randomUUID();

		for (int i = 0; i < 6; i++) {
			arrest(player, 3);
		}
		assertEquals(-0.26, learner.delta(player), 0.005);
	}

	@Test
	@DisplayName("W4: one arrest at peak 3 barely moves a stranger: delta -0.55 / 6")
	void w4OneArrest() {
		UUID player = UUID.randomUUID();

		arrest(player, 3);
		assertEquals(-0.55 / 6, learner.delta(player), EPS);
	}

	@Test
	@DisplayName("W5: escapes from 1-star chases alone never reach a known face")
	void w5FarmingLevelOne() {
		UUID player = UUID.randomUUID();

		for (int i = 0; i < 100; i++) {
			escape(player, 1, 20);
			double n = habit(player).n();
			assertTrue(learner.delta(player) <= 0.1 * n / (n + 5) + 1e-9);
			assertTrue(learner.delta(player) < 0.067);
		}
	}

	@ParameterizedTest(name = "W6: {0} learns nothing")
	@EnumSource(value = WantedCause.class, names = {"DEATH", "ARREST", "BRIBE"})
	@DisplayName("W6: death at any peak and a 1-star arrest or bribe learn nothing")
	void w6CheapLossesLearnNothing(WantedCause end) {
		UUID player = UUID.randomUUID();
		int  peak   = end == WantedCause.DEATH ? 3 : 1;

		chase(player, WantedCause.CRIME, end, peak, 60, 40);

		assertEquals(0, learner.delta(player), 0);
		assertTrue(habits.get().isEmpty());
		assertTrue(levels.get().isEmpty());
	}

	@Test
	@DisplayName("a 2-star arrest does count")
	void twoStarArrestCounts() {
		UUID player = UUID.randomUUID();

		arrest(player, 2);

		assertEquals(-0.75 / 6, learner.delta(player), EPS);
		assertEquals(1, level(2).n(), EPS);
		assertEquals(0, level(2).escaped(), EPS);
	}

	@Test
	@DisplayName("DECAY counts as half an escape and teaches no typical length")
	void decayIsHalf() {
		UUID player = UUID.randomUUID();

		chase(player, WantedCause.CRIME, WantedCause.DECAY, 3, 60, 40);

		assertEquals((0.5 - 0.55) / 6, learner.delta(player), EPS);
		assertEquals(0.5, level(3).escaped(), EPS);
		assertEquals(0, level(3).typicalCount(), 0);
		assertEquals(AUTO.typicalFor(3), learner.typicalSeconds(3, AUTO), 0);
	}

	@Test
	@DisplayName("n stays at most 10 over 1000 chases at decay 0.90")
	void habitCountBounded() {
		UUID player = UUID.randomUUID();

		for (int i = 0; i < 1000; i++) {
			escape(player, 3, 60);
			assertTrue(habit(player).n() <= 10, "n = " + habit(player).n());
		}
		assertEquals(10, habit(player).n(), EPS);
	}

	@Test
	@DisplayName("typical length moves from every getaway by its contact time, never by the hide after it")
	void typicalLearnsContactTime() {
		escape(UUID.randomUUID(), 3, 80);
		assertEquals(80, level(3).typicalSeconds(), EPS);

		// a long hide after the last loss of sight (chase 600 s, contact still 80) does not move it
		chase(UUID.randomUUID(), WantedCause.CRIME, WantedCause.EVASION, 3, 600, 80);
		assertEquals(80, level(3).typicalSeconds(), EPS);

		// a longer contact time steps it up by 10 %, a shorter one down by 10 %
		escape(UUID.randomUUID(), 3, 200);
		assertEquals(88, level(3).typicalSeconds(), EPS);
		escape(UUID.randomUUID(), 3, 10);
		assertEquals(79.2, level(3).typicalSeconds(), EPS);
		assertEquals(4, level(3).typicalCount(), EPS);
	}

	@Test
	@DisplayName("typicalCount is capped at 100")
	void typicalCountCapped() {
		for (int i = 0; i < 120; i++) {
			escape(UUID.randomUUID(), 2, 60);
		}
		assertEquals(100, level(2).typicalCount(), 0);
	}

	@Test
	@DisplayName("T(peak) is clamped to half and double Typical_Seconds")
	void typicalClamped() {
		learner.load(List.of(), List.of(new ChaseLevelStat(3, 100, 50, 10, 100, now),
		                                new ChaseLevelStat(4, 100, 30, 1000, 100, now)));

		assertEquals(45, learner.typicalSeconds(3, AUTO), 0);
		assertEquals(240, learner.typicalSeconds(4, AUTO), 0);
	}

	@Test
	@DisplayName("chases shorter than Min_Chase_Seconds are not learned from")
	void minChaseSeconds() {
		UUID player = UUID.randomUUID();

		chase(player, WantedCause.CRIME, WantedCause.EVASION, 3, 29, 20);
		assertTrue(habits.get().isEmpty());

		chase(player, WantedCause.CRIME, WantedCause.EVASION, 3, 30, 20);
		assertEquals(1, habit(player).n(), 0);
	}

	@Test
	@DisplayName("a player's chases closer than Min_Seconds_Between_Outcomes are not learned from")
	void minSecondsBetweenOutcomes() {
		UUID   player = UUID.randomUUID();
		long   gap    = AUTO.learning().minSecondsBetweenOutcomes() * 1000L;

		escape(player, 3, 60);
		long first = now;

		learner.record(new ChaseRecord(player, WantedCause.CRIME, WantedCause.EVASION, 3, 60_000, 60_000,
		                               first + gap - 1), first + gap - 1);
		assertEquals(1, habit(player).n(), 0);
		assertEquals(first, habit(player).lastAt());

		learner.record(new ChaseRecord(player, WantedCause.CRIME, WantedCause.EVASION, 3, 60_000, 60_000,
		                               first + gap), first + gap);
		assertEquals(1.9, habit(player).n(), EPS);
	}

	@ParameterizedTest(name = "end cause {0} is not learned")
	@EnumSource(value = WantedCause.class, names = {"ADMIN", "SIGN", "RESTORE", "UNKNOWN", "CRIME"})
	void uncountedEnds(WantedCause end) {
		chase(UUID.randomUUID(), WantedCause.CRIME, end, 3, 60, 40);

		assertTrue(habits.get().isEmpty());
		assertTrue(levels.get().isEmpty());
	}

	@ParameterizedTest(name = "start cause {0} is not learned")
	@EnumSource(value = WantedCause.class, names = {"CRIME"}, mode = EnumSource.Mode.EXCLUDE)
	void nonCrimeStarts(WantedCause start) {
		chase(UUID.randomUUID(), start, WantedCause.EVASION, 3, 60, 40);

		assertTrue(habits.get().isEmpty());
		assertTrue(levels.get().isEmpty());
	}

	@Test
	@DisplayName("a heavy player's chase counts for less on the server row (fair share 4 / n)")
	void fairShareWeight() {
		UUID heavy = UUID.randomUUID();
		learner.load(List.of(new ChaseHabit(heavy, 8, 8, 4, 0)), List.of());

		escape(heavy, 3, 60);

		assertEquals(0.5, level(3).n(), EPS);
		assertEquals(0.5, level(3).escaped(), EPS);
		assertEquals(0.5, level(3).typicalCount(), EPS);
		assertEquals(8 * 0.9 + 1, habit(heavy).n(), EPS);
	}

	@Test
	@DisplayName("replay determinism: the same chases give the same rows")
	@SuppressWarnings("unchecked")
	void replayDeterminism() {
		List<UUID> players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		List<List<Object>> first  = replay(learner, players);

		IRepository<ChaseHabit>     habitRepo2 = mock(IRepository.class);
		IRepository<ChaseLevelStat> levelRepo2 = mock(IRepository.class);
		ChaseLearner                second     = newLearner(loader, habitRepo2, levelRepo2);
		assertEquals(first, replay(second, players));
	}

	private List<List<Object>> replay(ChaseLearner target, List<UUID> players) {
		WantedCause[] ends = {WantedCause.EVASION, WantedCause.ARREST, WantedCause.DECAY, WantedCause.BRIBE};
		long          t    = 0;
		for (int i = 0; i < 60; i++) {
			t += 200_000L;
			UUID player = players.get(i % players.size());
			target.record(new ChaseRecord(player, WantedCause.CRIME, ends[i % ends.length], 1 + i % 5, 60_000,
			                              20_000 + i * 1_000L, t), t);
		}

		List<List<Object>> out = new ArrayList<>();
		for (UUID player : players) {
			out.add(List.of(target.delta(player)));
		}
		for (int peak = 1; peak <= 5; peak++) {
			out.add(List.of(target.typicalSeconds(peak, AUTO)));
		}
		return out;
	}

	@Test
	@DisplayName("Learning.Enable: false reads cold values and writes nothing")
	void learningDisabled() {
		UUID player = UUID.randomUUID();
		escape(player, 3, 70);
		learner.load(List.of(), List.of(new ChaseLevelStat(4, 100, 30, 200, 100, now)));
		List<ChaseHabit>     habitsBefore = List.copyOf(habits.get());
		List<ChaseLevelStat> levelsBefore = List.copyOf(levels.get());

		configure(DropMode.AUTO, false);
		escape(player, 3, 70);
		arrest(UUID.randomUUID(), 3);

		assertEquals(0, learner.delta(player), 0);
		assertEquals(AUTO.typicalFor(4), learner.typicalSeconds(4, config(DropMode.AUTO, false).evasion().auto()), 0);
		assertEquals(habitsBefore, List.copyOf(habits.get()), "the caches stay in memory, untouched");
		assertEquals(levelsBefore, List.copyOf(levels.get()));
		verify(habitRepo).setDataSupplier(org.mockito.ArgumentMatchers.any());
		verify(levelRepo).setDataSupplier(org.mockito.ArgumentMatchers.any());
		verifyNoMoreInteractions(habitRepo, levelRepo);
	}

	@ParameterizedTest(name = "nothing is learned under Drop_Mode {0}")
	@EnumSource(value = DropMode.class, names = {"AUTO"}, mode = EnumSource.Mode.EXCLUDE)
	void noLearningOutsideAuto(DropMode mode) {
		configure(mode, true);
		UUID player = UUID.randomUUID();

		escape(player, 3, 70);
		arrest(UUID.randomUUID(), 3);

		assertTrue(habits.get().isEmpty());
		assertTrue(levels.get().isEmpty());
	}
}
