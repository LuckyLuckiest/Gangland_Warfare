package org.luckyraven.gangland.turf.task;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the lifecycle of {@link TurfLocationTracker}: {@code start()} registers the tracker as a Bukkit listener so its
 * {@code onQuit} actually runs (TF-06 — the class is a {@code @Bean} without {@code @ListenerHandler}, so nothing else
 * registers it), and {@code onShutdown()} cancels the 1 Hz task (TF-07).
 */
@DisplayName("TurfLocationTracker — quit listener registration and shutdown (TF-06, TF-07)")
class TurfLocationTrackerTest {

	@Test
	@DisplayName("start: registers the tracker as a Bukkit listener so PlayerQuitEvent reaches onQuit (TF-06)")
	void start_registersQuitListener() {
		JavaPlugin          plugin  = mock(JavaPlugin.class);
		TurfLocationTracker tracker = new TurfLocationTracker(plugin, mock(TurfManager.class), null);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			tracker.start();

			verify(bukkit.pluginManager()).registerEvents(tracker, plugin);
		}
	}

	@Test
	@DisplayName("onShutdown: cancels the scheduled task (TF-07)")
	void onShutdown_cancelsTask() {
		JavaPlugin          plugin  = mock(JavaPlugin.class);
		TurfLocationTracker tracker = new TurfLocationTracker(plugin, mock(TurfManager.class), null);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			BukkitTask task = mock(BukkitTask.class);
			when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
					.thenReturn(task);
			tracker.start();

			tracker.onShutdown();

			verify(task).cancel();
		}
	}
}
