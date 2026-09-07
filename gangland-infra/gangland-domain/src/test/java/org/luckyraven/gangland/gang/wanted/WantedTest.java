package org.luckyraven.gangland.gang.wanted;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link Wanted#buildStars(int, int)} and {@link Wanted#setLevel(int)}'s clamping / wanted-flag
 * transitions (Test Surface, wanted-bounty-combat.md: "Wanted.buildStars(level, maxLevel) - negative
 * maxLevel, level &gt; maxLevel, level &lt; 0" and "Wanted.setLevel clamping and the wanted flag
 * transition, with owner == null so no Bukkit call is reached").
 *
 * <p><b>Unverified — module ownership.</b> {@code gangland-infra/gangland-domain} is owned by a
 * different agent for Maven runs in this initiative; this suite could not be compiled or executed
 * and is reported as an unverified draft.
 *
 * <p>Every test here builds a {@code Wanted} with {@code owner == null} (never set), so
 * {@code setLevel}'s event-firing branches (which all guard on {@code owner != null}) are never
 * reached and no {@code Bukkit} static is touched — matching the audit's suggested seam-free
 * approach for this class.
 */
@DisplayName("Wanted - star rendering and level clamping")
class WantedTest {

	@Test
	@DisplayName("buildStars renders exactly maxLevel characters, filled up to level")
	void buildStars_withinRange_fillsExactlyLevelStars() {
		assertEquals("★★☆☆☆", Wanted.buildStars(2, 5));
		assertEquals("☆☆☆☆☆", Wanted.buildStars(0, 5));
		assertEquals("★★★★★", Wanted.buildStars(5, 5));
	}

	@Test
	@DisplayName("a negative maxLevel clamps to zero stars total")
	void buildStars_negativeMaxLevel_rendersEmptyString() {
		assertEquals("", Wanted.buildStars(3, -5));
	}

	@Test
	@DisplayName("level greater than maxLevel clamps the filled count to maxLevel, no overflow")
	void buildStars_levelAboveMax_clampsFilledToMax() {
		assertEquals("★★★", Wanted.buildStars(99, 3));
	}

	@Test
	@DisplayName("a negative level clamps the filled count to zero, not a negative repeat count")
	void buildStars_negativeLevel_clampsFilledToZero() {
		assertEquals("☆☆☆☆", Wanted.buildStars(-4, 4));
	}

	@Test
	@DisplayName("a fresh Wanted starts at level 0, not wanted, with the configured increments/maxLevel")
	void constructor_initializesToZeroAndNotWanted() {
		Wanted wanted = new Wanted(null, 1, 5);

		assertEquals(0, wanted.getLevel());
		assertFalse(wanted.isWanted());
		assertEquals(1, wanted.getIncrements());
		assertEquals(5, wanted.getMaxLevel());
	}

	@Test
	@DisplayName("setLevel clamps into [0, maxLevel] with no owner set")
	void setLevel_clampsIntoZeroToMaxLevel_withNoOwner() {
		Wanted wanted = new Wanted(null, 1, 5);

		wanted.setLevel(9001);
		assertEquals(5, wanted.getLevel());

		wanted.setLevel(-100);
		assertEquals(0, wanted.getLevel());
	}

	@Test
	@DisplayName("the wanted flag tracks level > 0, flipping on both the 0->N and N->0 transitions")
	void setLevel_wantedFlagTracksLevelAboveZero() {
		Wanted wanted = new Wanted(null, 1, 5);

		wanted.setLevel(3);
		assertTrue(wanted.isWanted());

		wanted.setLevel(0);
		assertFalse(wanted.isWanted());
	}

	@Test
	@DisplayName("incrementLevel adds the configured increment and clamps at maxLevel")
	void incrementLevel_addsIncrementsAndClampsAtMax() {
		Wanted wanted = new Wanted(null, 2, 5);

		wanted.incrementLevel();
		assertEquals(2, wanted.getLevel());

		wanted.incrementLevel();
		wanted.incrementLevel();
		assertEquals(5, wanted.getLevel(), "3rd increment would be 6, clamped to maxLevel 5");
	}

	@Test
	@DisplayName("decrementLevel subtracts one and clamps at zero, never going negative")
	void decrementLevel_subtractsOneAndClampsAtZero() {
		Wanted wanted = new Wanted(null, 1, 5);
		wanted.setLevel(1);

		wanted.decrementLevel();
		assertEquals(0, wanted.getLevel());

		wanted.decrementLevel();
		assertEquals(0, wanted.getLevel(), "decrementing below zero must clamp, not go negative");
	}

	@Test
	@DisplayName("getLevelStars delegates to buildStars using the current level and maxLevel")
	void getLevelStars_delegatesToBuildStars() {
		Wanted wanted = new Wanted(null, 1, 3);
		wanted.setLevel(2);

		assertEquals(Wanted.buildStars(2, 3), wanted.getLevelStars());
	}

	@Test
	@DisplayName("reset() zeroes the level, clears the wanted flag, and leaves a null timer alone (no NPE)")
	void reset_zeroesLevelAndWantedFlag() {
		Wanted wanted = new Wanted(null, 1, 5);
		wanted.setLevel(4);

		wanted.reset();

		assertEquals(0, wanted.getLevel());
		assertFalse(wanted.isWanted());
		assertDoesNotThrow(wanted::stopTimer, "stopTimer must no-op safely when no timer was ever created");
	}

}
