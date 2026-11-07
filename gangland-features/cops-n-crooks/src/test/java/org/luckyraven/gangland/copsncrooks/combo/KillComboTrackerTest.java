package org.luckyraven.gangland.copsncrooks.combo;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves {@link KillComboTracker#addKill(Entity, int)}'s counters and {@code KillRecord} contents
 * (Test Surface, wanted-bounty-combat.md: "KillComboTracker.addKill counters and KillRecord
 * contents").
 *
 * <p><b>Unverified — module ownership.</b> {@code gangland-features/cops-n-crooks} is owned by a
 * different agent for Maven runs in this initiative; this suite could not be compiled or executed
 * and is reported as an unverified draft.
 *
 * <p><b>Scope note:</b> the constructor builds a {@code CountdownTimer} but only stores its fields
 * (verified by reading {@code CountdownTimer}'s constructor) — no Bukkit scheduler call happens
 * until {@code start()}, so a bare {@code new KillComboTracker(...)} plus {@code addKill(...)}
 * needs no {@code BukkitStatics} seam. {@code restartTimer()}/{@code stopTimer()} do call into the
 * scheduler and are out of scope for this pass; likewise the private
 * {@code KillCombo.shouldTriggerWantedLevel} (Test Surface bullet also names this) is unreachable
 * without going through {@code KillCombo.recordKill}, which unconditionally calls
 * {@code tracker.restartTimer()} at its end — exercising it would require Bukkit scheduler mocking
 * this pass did not budget for.
 */
@DisplayName("KillComboTracker - kill counters and history (addKill)")
class KillComboTrackerTest {

	private final JavaPlugin plugin = mock(JavaPlugin.class);
	private final Player player = mock(Player.class);

	@Test
	@DisplayName("a fresh tracker starts at zero kills with empty history")
	void constructor_startsAtZeroWithEmptyHistory() {
		KillComboTracker tracker = tracker();

		assertEquals(0, tracker.getNormalKillCount());
		assertEquals(0, tracker.getPointKillCount());
		assertTrue(tracker.getKillHistory().isEmpty());
	}

	@Test
	@DisplayName("addKill increments both the normal kill count and the point kill count by the given points")
	void addKill_incrementsNormalAndPointCounts() {
		KillComboTracker tracker = tracker();

		tracker.addKill(entity(EntityType.ZOMBIE), 1);
		tracker.addKill(entity(EntityType.PLAYER), 3);

		assertEquals(2, tracker.getNormalKillCount(), "normalKillCount increments by one per kill regardless of points");
		assertEquals(4, tracker.getPointKillCount(), "pointKillCount accumulates the points argument");
	}

	@Test
	@DisplayName("each addKill appends a KillRecord carrying the entity type and points")
	void addKill_appendsKillRecordWithTypeAndPoints() {
		KillComboTracker tracker = tracker();

		tracker.addKill(entity(EntityType.ZOMBIE), 2);

		assertEquals(1, tracker.getKillHistory().size());
		KillComboTracker.KillRecord record = tracker.getKillHistory().get(0);
		assertEquals(EntityType.ZOMBIE, record.entityType());
		assertEquals(2, record.points());
		assertTrue(record.timestamp() > 0);
	}

	@Test
	@DisplayName("killHistory is unbounded and grows by exactly one entry per addKill call")
	void addKill_historyGrowsByOnePerCall() {
		KillComboTracker tracker = tracker();

		for (int i = 0; i < 5; i++) {
			tracker.addKill(entity(EntityType.ZOMBIE), 1);
		}

		assertEquals(5, tracker.getKillHistory().size());
	}

	@Test
	@DisplayName("WB-07: the combo window is the configured number of seconds, not 20 times it")
	void getRemainingTime_isConfiguredSecondsNotTicks() {
		KillComboTracker tracker = tracker();

		assertEquals(10, tracker.getRemainingTime(),
		             "the countdown ticks once per second, so Reset_After seconds must be the initial time left");
	}

	@Test
	@DisplayName("WB-07: restartTimer schedules on the main thread because the expiry callback mutates a HashMap")
	void restartTimer_schedulesOnMainThread() {
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		BukkitTask      task      = mock(BukkitTask.class);
		when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
		when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

			tracker().restartTimer();

			verify(scheduler, never()).runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), anyLong(),
			                                                      anyLong());
			verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong());
		}
	}

	private KillComboTracker tracker() {
		return new KillComboTracker(plugin, player, null, 10);
	}

	private static Entity entity(EntityType type) {
		Entity entity = mock(Entity.class);
		when(entity.getType()).thenReturn(type);
		return entity;
	}

}
