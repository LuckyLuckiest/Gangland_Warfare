package org.luckyraven.gangland.core.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.TestLevelUpEvent;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link Level}'s XP-curve arithmetic and the boundary behaviours flagged by the
 * users-levels-economy-bank.md audit (Observation #8: {@code addLevels} now clamps on the resulting level
 * instead of the iteration count (GI 3), though a reused/cancelled event still consumes XP every iteration -
 * that half of #8 is untouched here; Observation #9: {@code getPercentage} divides by zero when the formula
 * evaluates to 0).
 *
 * <p>Uses the {@code Level(int maxLevel, double baseAmount)} constructor with an explicit
 * {@link Level#setFormula(String)} throughout, so none of these tests need {@code GangSettings} bound.
 */
@DisplayName("Level - XP curve and level progression")
class LevelTest {

	@Test
	@DisplayName("experienceCalculation evaluates the configured formula with base/max/level/experience bound as variables")
	void experienceCalculation_evaluatesFormula() {
		Level level = new Level(100, 1000);
		level.setFormula("base * level");

		assertEquals(5000D, level.experienceCalculation(5));
	}

	@Test
	@DisplayName("addExperience(levelUp=false) only accumulates XP, no progression check runs")
	void addExperience_withoutLevelUp_onlyAccumulates() {
		Level level = new Level(100, 1000);
		level.setFormula("100");

		level.addExperience(50, false, null);

		assertEquals(50D, level.getExperience());
		assertEquals(0, level.getLevelValue());
	}

	@Test
	@DisplayName("addExperience cascades across every affordable level in one call when the event is null (unconditional increment, no Bukkit dispatch)")
	void addExperience_cascadesAcrossLevels_withNullEvent() {
		Level level = new Level(100, 1000);
		level.setFormula("100"); // fixed 100 XP per level regardless of level/base

		level.addExperience(250, null);

		assertEquals(2, level.getLevelValue());
		assertEquals(50D, level.getExperience(), 0.0001);
	}

	@Test
	@DisplayName("removeExperience floors at zero, never negative")
	void removeExperience_floorsAtZero() {
		Level level = new Level(100, 1000);
		level.setFormula("100");
		level.addExperience(30, false, null);

		level.removeExperience(1000);

		assertEquals(0D, level.getExperience());
	}

	@Test
	@DisplayName("US-09 gap: addExperience(NaN/Infinity) is a no-op, never stores a non-finite value")
	void addExperience_nonFinite_isNoOp() {
		Level level = new Level(100, 1000);
		level.setFormula("100");
		level.addExperience(50, false, null);

		level.addExperience(Double.NaN, false, null);
		level.addExperience(Double.POSITIVE_INFINITY, false, null);
		level.addExperience(Double.NEGATIVE_INFINITY, false, null);

		assertEquals(50D, level.getExperience());
	}

	@Test
	@DisplayName("US-09 gap: removeExperience(NaN/Infinity) is a no-op, never poisons experience")
	void removeExperience_nonFinite_isNoOp() {
		Level level = new Level(100, 1000);
		level.setFormula("100");
		level.addExperience(50, false, null);

		level.removeExperience(Double.NaN);
		level.removeExperience(Double.POSITIVE_INFINITY);

		assertEquals(50D, level.getExperience());
	}

	@Test
	@DisplayName("US-09 gap: setExperience maps a non-finite value to 0, cleaning rows already poisoned with Infinity/NaN")
	void setExperience_nonFinite_mapsToZero() {
		Level level = new Level(100, 1000);

		level.setExperience(Double.POSITIVE_INFINITY);
		assertEquals(0D, level.getExperience());

		level.setExperience(Double.NEGATIVE_INFINITY);
		assertEquals(0D, level.getExperience());

		level.setExperience(Double.NaN);
		assertEquals(0D, level.getExperience());

		level.setExperience(42D);
		assertEquals(42D, level.getExperience());
	}

	@Test
	void nextLevel_clampsAtMaxLevel() {
		Level level = new Level(5, 1000);
		level.setLevelValue(5);

		assertEquals(5, level.nextLevel());
	}

	@Test
	void previousLevel_neverGoesBelowZero() {
		Level level = new Level(5, 1000);

		assertEquals(0, level.previousLevel());
	}

	@Test
	@DisplayName("Observation #9 (users-levels-economy-bank.md): getPercentage is NaN when the formula evaluates to 0 XP required (0/0)")
	void getPercentage_isNaN_whenRequirementIsZero() {
		Level level = new Level(100, 1000);
		level.setFormula("0");

		double percentage = level.getPercentage();

		assertTrue(Double.isNaN(percentage), "0/0 division pinned as NaN until a formula/level guard is added");
	}

	@Test
	@DisplayName("T-277: addLevels with a null event levels without firing an event")
	void addLevels_nullEvent_levelsWithoutAnEvent() {
		Level level = new Level(5, 1000);
		level.setFormula("0");

		int counter = level.addLevels(2, null);

		assertEquals(2, counter);
		assertEquals(2, level.getLevelValue());
	}

	@Test
	@DisplayName("T-255: getPercentage reads 100 at max level, never above")
	void getPercentage_atMaxLevel_is100() {
		Level level = new Level(1, 1000);
		level.setFormula("100");
		level.setLevelValue(1);
		level.setExperience(250);

		assertEquals(100D, level.getPercentage(), 0.0001);
	}

	@Nested
	@DisplayName("addLevels — Observation #8 (users-levels-economy-bank.md)")
	class AddLevelsTest {

		@Test
		@DisplayName("clamps on the resulting level, not the iteration count: levelValue never overshoots maxLevel")
		void addLevels_clampsAtMaxLevel() {
			try (BukkitStatics bukkit = BukkitStatics.install()) {
				Level level = new Level(5, 1000);
				level.setFormula("0"); // always affordable
				level.setLevelValue(3);

				TestLevelUpEvent event = new TestLevelUpEvent(level);
				int counter = level.addLevels(10, event);

				assertEquals(2, counter, "only 2 more levels fit between levelValue=3 and maxLevel=5");
				assertEquals(5, level.getLevelValue(), "levelValue is clamped at maxLevel, never overshoots");
			}
		}

		@Test
		@DisplayName("T-253/US-43: a pre-cancelled event undoes its own step and stops: no level, no XP spent, counter 0")
		void addLevels_cancelledEvent_changesNothing() {
			try (BukkitStatics bukkit = BukkitStatics.install()) {
				Level level = new Level(10, 1000);
				level.setFormula("50"); // fixed requirement per level
				level.addExperience(200, false, null); // enough for 4 levels' worth of XP

				TestLevelUpEvent event = new TestLevelUpEvent(level);
				event.setCancelled(true);

				int counter = level.addLevels(3, event);

				assertEquals(0, counter, "the first cancelled attempt stops the loop and counts nothing");
				assertEquals(0, level.getLevelValue(), "cancelled, so the level never advances");
				assertEquals(200D, level.getExperience(), 0.0001, "a cancelled level-up eats no XP");
			}
		}
	}

	@Nested
	@DisplayName("addExperience(event) — handleLevelProgression cancellation semantics")
	class AddExperienceWithEventTest {

		@Test
		@DisplayName("T-253: a cancelled level-up rolls back its own step and stops: level stays, XP untouched")
		void addExperience_cancelledEvent_changesNothing() {
			try (BukkitStatics bukkit = BukkitStatics.install()) {
				Level level = new Level(10, 1000);
				level.setFormula("100"); // fixed requirement, independent of levelValue

				TestLevelUpEvent event = new TestLevelUpEvent(level);
				event.setCancelled(true);

				level.addExperience(250, event);

				assertEquals(0, level.getLevelValue(), "cancelled, so the level never advances");
				assertEquals(250D, level.getExperience(), 0.0001, "a cancelled level-up eats no XP");
			}
		}

		@Test
		@DisplayName("T-253: the listener reads the level just reached, once per level (seen [1, 2] for a two-level jump)")
		void addExperience_listenerReadsTheLevelJustReached() {
			try (BukkitStatics bukkit = BukkitStatics.install()) {
				Level level = new Level(10, 1000);
				level.setFormula("100");
				List<Integer> seen = new ArrayList<>();
				TestLevelUpEvent event = new TestLevelUpEvent(level);
				doAnswer(inv -> {
					seen.add(event.getLevel().getLevelValue());
					return null;
				}).when(bukkit.pluginManager()).callEvent(any());

				level.addExperience(250, event);

				assertEquals(List.of(1, 2), seen);
				assertEquals(2, level.getLevelValue());
				assertEquals(50D, level.getExperience(), 0.0001);
			}
		}

		@Test
		@DisplayName("T-255: a player at max level collects no XP")
		void addExperience_atMaxLevel_addsNothing() {
			Level level = new Level(1, 1000);
			level.setFormula("100");
			level.setLevelValue(1);

			level.addExperience(500, false, null);

			assertEquals(0D, level.getExperience(), 0.0001);
		}
	}

}
