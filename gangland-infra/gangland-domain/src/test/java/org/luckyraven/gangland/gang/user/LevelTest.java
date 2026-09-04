package org.luckyraven.gangland.gang.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.support.TestLevelUpEvent;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link Level}'s XP-curve arithmetic and the boundary behaviours flagged by the
 * users-levels-economy-bank.md audit (Observation #8: {@code addLevels} clamps on iteration count, not the
 * resulting level, and a reused/cancelled event still consumes XP; Observation #9: {@code getPercentage} divides
 * by zero when the formula evaluates to 0).
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

	@Nested
	@DisplayName("addLevels — Observation #8 (users-levels-economy-bank.md)")
	class AddLevelsTest {

		@Test
		@DisplayName("breaks on the iteration COUNT, not the resulting level, so levelValue is never clamped to maxLevel")
		void addLevels_overshootsMaxLevel_becauseBreakUsesIterationCounter() {
			try (BukkitStatics bukkit = BukkitStatics.install()) {
				Level level = new Level(5, 1000);
				level.setFormula("0"); // always affordable
				level.setLevelValue(3);

				TestLevelUpEvent event = new TestLevelUpEvent(level);
				int counter = level.addLevels(10, event);

				assertEquals(5, counter, "the loop runs exactly maxLevel times regardless of the starting level");
				assertEquals(8, level.getLevelValue(),
						"starting at 3 plus 5 more increments overshoots maxLevel=5 - this pins the bug, not an intended clamp");
			}
		}

		@Test
		@DisplayName("one LevelUpEvent instance is reused for the whole loop: a pre-cancelled event stays cancelled (sticky) for every iteration, yet XP is subtracted before the cancellation check runs")
		void addLevels_cancelledEvent_stillConsumesExperienceEveryIteration() {
			try (BukkitStatics bukkit = BukkitStatics.install()) {
				Level level = new Level(10, 1000);
				level.setFormula("50"); // fixed requirement per level
				level.addExperience(200, false, null); // enough for 4 levels' worth of XP

				TestLevelUpEvent event = new TestLevelUpEvent(level);
				event.setCancelled(true);

				int counter = level.addLevels(3, event);

				assertEquals(3, counter, "the loop still iterates 3 times even though every attempt is cancelled");
				assertEquals(0, level.getLevelValue(), "cancelled on every iteration, so the level never advances");
				assertEquals(50D, level.getExperience(), 0.0001,
						"200 - 3*50 = 50: XP is subtracted before event.isCancelled() is consulted (line-order bug)");
			}
		}
	}

	@Nested
	@DisplayName("addExperience(event) — handleLevelProgression cancellation semantics")
	class AddExperienceWithEventTest {

		@Test
		@DisplayName("a permanently-cancelled event drains XP every loop pass (levelValue never advances, so nextLevelAmount never changes), not just once")
		void addExperience_cancelledEvent_keepsDrainingUntilBelowRequirement() {
			try (BukkitStatics bukkit = BukkitStatics.install()) {
				Level level = new Level(10, 1000);
				level.setFormula("100"); // fixed requirement, independent of levelValue

				TestLevelUpEvent event = new TestLevelUpEvent(level);
				event.setCancelled(true);

				level.addExperience(250, event);

				assertEquals(0, level.getLevelValue(), "every attempt is cancelled, so the level never advances");
				assertEquals(50D, level.getExperience(), 0.0001,
						"250 - 100 - 100 = 50: the while-loop keeps re-attempting and re-deducting the same "
						+ "unreachable level's cost because nothing distinguishes an already-cancelled attempt "
						+ "from a fresh one");
			}
		}
	}

}
