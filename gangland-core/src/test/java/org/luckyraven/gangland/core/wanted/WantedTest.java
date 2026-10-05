package org.luckyraven.gangland.core.wanted;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
 * <p>The clamping tests build a {@code Wanted} with {@code owner == null} (never set), so
 * {@code setLevel}'s event-firing branches (which all guard on {@code owner != null}) are never
 * reached and no {@code Bukkit} static is touched. The cause tests (CONTRACTS C2) set an owner and
 * capture the fired events through {@link BukkitStatics}.
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

	@Test
	@DisplayName("setLevel with a cause passes it to the change, start and end events")
	void setLevelWithCause_changeStartAndEndEventsCarryTheCause() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::isPrimaryThread).thenReturn(true);
			List<Event> events = capture(bukkit);
			Wanted      wanted = owned(mock(JavaPlugin.class));

			wanted.setLevel(2, WantedCause.CRIME);
			wanted.setLevel(0, WantedCause.ARREST);

			assertEquals(4, events.size(), events.toString());
			WantedLevelChangeEvent up = assertInstanceOf(WantedLevelChangeEvent.class, events.get(0));
			assertEquals(WantedCause.CRIME, up.getCause());
			assertEquals(WantedCause.CRIME, assertInstanceOf(WantedStartEvent.class, events.get(1)).getCause());
			WantedLevelChangeEvent down = assertInstanceOf(WantedLevelChangeEvent.class, events.get(2));
			assertEquals(WantedCause.ARREST, down.getCause());
			assertEquals(WantedCause.ARREST, assertInstanceOf(WantedEndEvent.class, events.get(3)).getCause());
		}
	}

	@Test
	@DisplayName("setLevel off the main thread re-schedules itself with the same cause")
	void setLevelOffThread_reschedulesWithTheSameCause() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::isPrimaryThread).thenReturn(false, true);
			List<Event> events = capture(bukkit);
			JavaPlugin  plugin = mock(JavaPlugin.class);
			Wanted      wanted = owned(plugin);

			wanted.setLevel(3, WantedCause.SIGN);

			verify(bukkit.scheduler()).runTask(eq(plugin), any(Runnable.class));
			assertEquals(3, wanted.getLevel());
			assertEquals(WantedCause.SIGN, assertInstanceOf(WantedLevelChangeEvent.class, events.get(0)).getCause());
			assertEquals(WantedCause.SIGN, assertInstanceOf(WantedStartEvent.class, events.get(1)).getCause());
		}
	}

	@Test
	@DisplayName("reset with a cause fires the end event with it and stops the decay timer")
	void resetWithCause_endEventCarriesItAndStopsTheTimer() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::isPrimaryThread).thenReturn(true);
			BukkitTask task = mock(BukkitTask.class);
			when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
					.thenReturn(task);
			Wanted wanted = new Wanted(mock(JavaPlugin.class), 1, 5);
			wanted.setLevel(2);
			wanted.setOwner(mock(Player.class));
			wanted.createTimer(10, timer -> {
			}).start(false);
			List<Event> events = capture(bukkit);

			wanted.reset(WantedCause.DEATH);

			assertEquals(0, wanted.getLevel());
			assertEquals(WantedCause.DEATH, assertInstanceOf(WantedEndEvent.class, events.get(1)).getCause());
			assertNull(wanted.getRepeatingTimer());
			verify(task).cancel();
		}
	}

	@Test
	@DisplayName("the legacy no-cause setLevel fires its events with UNKNOWN")
	void legacySetLevel_firesUnknownCause() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(Bukkit::isPrimaryThread).thenReturn(true);
			List<Event> events = capture(bukkit);
			Wanted      wanted = owned(mock(JavaPlugin.class));

			wanted.setLevel(2);

			assertEquals(WantedCause.UNKNOWN, assertInstanceOf(WantedLevelChangeEvent.class, events.get(0)).getCause());
			assertEquals(WantedCause.UNKNOWN, assertInstanceOf(WantedStartEvent.class, events.get(1)).getCause());
		}
	}

	private static Wanted owned(JavaPlugin plugin) {
		Wanted wanted = new Wanted(plugin, 1, 5);
		wanted.setOwner(mock(Player.class));
		return wanted;
	}

	private static List<Event> capture(BukkitStatics bukkit) {
		List<Event> events = new ArrayList<>();
		doAnswer(invocation -> events.add(invocation.getArgument(0))).when(bukkit.pluginManager()).callEvent(any());
		return events;
	}

}
