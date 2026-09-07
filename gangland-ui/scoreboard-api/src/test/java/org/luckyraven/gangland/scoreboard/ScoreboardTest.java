package org.luckyraven.gangland.scoreboard;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how {@link Scoreboard} schedules its per-tick refresh.
 *
 * <p>Observation #2 (ui-inventory-scoreboard.md) / UI-02: {@code start()} called {@code timer.start(true)}, i.e.
 * {@code runTaskTimerAsynchronously} at a one-tick period. The task body resolves placeholders (gang/user/bank
 * lookups and PlaceholderAPI expansions) and pushes FastBoard packets — none of it thread-safe — once per tick
 * per online player. The project's own {@code feedback_repeating_timer_async} rule reserves {@code start(true)}
 * for flag flips and cancels, so the board now ticks on the main thread.
 *
 * <p>The driver is deliberately {@code null}: {@code start()} never dereferences it, and constructing a real
 * {@code DriverHandler} would instantiate FastBoard, which needs a live NMS server.
 */
@DisplayName("Scoreboard - the per-tick refresh must run on the main thread (UI-02)")
class ScoreboardTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("UI-02: start() schedules a synchronous repeating task, never an asynchronous one")
	void start_schedulesSynchronously() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			JavaPlugin plugin = PluginMocks.plugin(tempDir);

			// BukkitRunnable reads the task id straight off the returned task, so it must not be null.
			when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
					.thenReturn(mock(BukkitTask.class));

			Scoreboard scoreboard = new Scoreboard(plugin, null);

			scoreboard.start();

			verify(bukkit.scheduler()).runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(1L));
			verify(bukkit.scheduler(), never())
					.runTaskTimerAsynchronously(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
		}
	}

}
