package org.luckyraven.gangland.copsncrooks.wanted.learn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChargeSheetSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.DropMode;
import org.luckyraven.gangland.copsncrooks.wanted.config.EvasionSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.HudSettings;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link ChaseLearner} as a bean lifecycle: loads on the first load only, prunes stale rows, never on a reload. */
@DisplayName("ChaseLearner lifecycle")
class ChaseLearnerLifecycleTest {

	private static final UUID   FRESH = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
	private static final UUID   STALE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
	private static final long   DAY   = 86_400_000L;

	private ChaseConfigLoader                    loader;
	private IRepository<ChaseHabit>              habitRepo;
	private IRepository<ChaseLevelStat>          levelRepo;
	private Supplier<Collection<ChaseHabit>>     habits;
	private Supplier<Collection<ChaseLevelStat>> levels;
	private ChaseLearner                         learner;

	private ChaseHabit freshHabit;
	private ChaseHabit staleHabit;
	private ChaseLevelStat freshLevel;
	private ChaseLevelStat staleLevel;

	private void configure(boolean learning) {
		AutoSettings          d = AutoSettings.DEFAULT;
		AutoSettings.Learning l = d.learning();
		AutoSettings auto = new AutoSettings(d.openingSeconds(), d.rampageCrimes(), d.rampagePeakLevel(),
		                                     d.lockCoolSeconds(), d.respotLimit(), d.petty(), d.coldTrail(),
		                                     d.cleanBreak(), d.momentum(), d.repeatChases(), d.repeatWindowMinutes(),
		                                     new AutoSettings.Learning(learning, l.escapeRate(),
		                                                               l.priorChases(), l.decayPerChase(),
		                                                               l.habitualEscaperDelta(),
		                                                               l.habitTimeStrength(), l.minTimeFactor(),
		                                                               l.maxTimeFactor(), l.minChaseSeconds(),
		                                                               l.minSecondsBetweenOutcomes(),
		                                                               l.forgetAfterDays()));
		EvasionSettings e = EvasionSettings.DEFAULT;
		when(loader.get()).thenReturn(new ChaseConfig(HeatSettings.DEFAULT,
		                                              new EvasionSettings(e.enabled(), e.lostSightSeconds(),
		                                                                  DropMode.AUTO, e.searchRadius(),
		                                                                  e.secondsToDrop(), e.outsideZoneSpeed(),
		                                                                  auto),
		                                              HudSettings.DEFAULT, ChargeSheetSettings.DEFAULT));
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		loader    = mock(ChaseConfigLoader.class);
		habitRepo = mock(IRepository.class);
		levelRepo = mock(IRepository.class);
		configure(true);

		learner = new ChaseLearner(loader, habitRepo, levelRepo);

		ArgumentCaptor<Supplier<Collection<ChaseHabit>>>     habitCaptor = ArgumentCaptor.forClass(Supplier.class);
		ArgumentCaptor<Supplier<Collection<ChaseLevelStat>>> levelCaptor = ArgumentCaptor.forClass(Supplier.class);
		verify(habitRepo).setDataSupplier(habitCaptor.capture());
		verify(levelRepo).setDataSupplier(levelCaptor.capture());
		habits = habitCaptor.getValue();
		levels = levelCaptor.getValue();

		long now = System.currentTimeMillis();
		long old = now - (AutoSettings.DEFAULT.learning().forgetAfterDays() + 1L) * DAY;
		freshHabit = new ChaseHabit(FRESH, 3, 3, 1.5, now);
		staleHabit = new ChaseHabit(STALE, 3, 3, 1.5, old);
		freshLevel = new ChaseLevelStat(2, 5, 3, 40, 3, now);
		staleLevel = new ChaseLevelStat(4, 5, 3, 40, 3, old);

		when(habitRepo.loadAll()).thenReturn(List.of(freshHabit, staleHabit));
		when(levelRepo.loadAll()).thenReturn(List.of(freshLevel, staleLevel));
	}

	@Test
	@DisplayName("it is a BeanLifecycle")
	void isBeanLifecycle() {
		assertInstanceOf(BeanLifecycle.class, learner);
	}

	@Test
	@DisplayName("the first load reads both tables into the caches")
	void firstLoad_fillsCaches() {
		learner.onInitialize(true);

		assertTrue(habits.get().contains(freshHabit));
		assertTrue(levels.get().contains(freshLevel));
		assertTrue(learner.delta(FRESH) > 0, "the loaded habit is used");
	}

	@Test
	@DisplayName("the first load deletes rows older than Forget_After_Days and keeps them out of the caches")
	void firstLoad_prunesStaleRows() {
		learner.onInitialize(true);

		verify(habitRepo).delete(staleHabit);
		verify(levelRepo).delete(staleLevel);
		verify(habitRepo, never()).delete(freshHabit);
		verify(levelRepo, never()).delete(freshLevel);
		assertEquals(List.of(freshHabit), List.copyOf(habits.get()));
		assertEquals(List.of(freshLevel), List.copyOf(levels.get()));
	}

	@Test
	@DisplayName("a reload does not read the tables again, so unsaved learning survives /glw reload")
	void reload_keepsCaches() {
		learner.onInitialize(true);
		long before = System.currentTimeMillis();
		learner.record(new ChaseRecord(UUID.randomUUID(), org.luckyraven.gangland.core.wanted.WantedCause.CRIME,
		                               org.luckyraven.gangland.core.wanted.WantedCause.EVASION, 2, 200_000L,
		                               60_000L, before), before);
		int size = habits.get().size();

		learner.onClear();
		learner.onInitialize(false);

		verify(habitRepo).loadAll();
		verify(levelRepo).loadAll();
		assertEquals(size, habits.get().size());
	}

	@Test
	@DisplayName("with Learning.Enable false the first load still fills the caches but deletes nothing")
	void learningOff_writesNothing() {
		configure(false);

		learner.onInitialize(true);

		verify(habitRepo, never()).delete(any());
		verify(levelRepo, never()).delete(any());
		assertEquals(2, habits.get().size());
	}
}
