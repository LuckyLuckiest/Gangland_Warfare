package org.luckyraven.gangland.turf.task;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the inactivity-release schedule of {@link GangPresenceTracker} (TF-09): the first release sweep runs five
 * minutes after the tracker starts, not a full day, so a server restarted daily still releases an inactive gang's turf.
 * The heartbeat keeps its one-minute cadence. Both tasks are scheduled on the Bukkit scheduler mock from
 * {@link BukkitStatics}.
 */
@DisplayName("GangPresenceTracker — release sweep timing and shutdown (TF-09, TF-07)")
class GangPresenceTrackerTest {

	private static final long FIRST_RELEASE_TICKS = 6000L;      // 5 minutes
	private static final long RELEASE_PERIOD_TICKS = 1728000L;  // 24 hours

	@Test
	@DisplayName("start: the first release sweep runs after five minutes, then every 24 hours (TF-09)")
	void start_firstReleaseRunsAfterFiveMinutes() {
		JavaPlugin plugin  = mock(JavaPlugin.class);
		Runnable   release = mock(Runnable.class);
		GangPresenceTracker tracker = new GangPresenceTracker(plugin,
		                                                      mock(GangLookupContract.class),
		                                                      mock(UserLookupContract.class),
		                                                      10,
		                                                      release);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			tracker.start();

			verify(bukkit.scheduler()).runTaskTimer(plugin, release, FIRST_RELEASE_TICKS, RELEASE_PERIOD_TICKS);
		}
	}

	@Test
	@DisplayName("onShutdown: cancels the heartbeat and the release task (TF-07)")
	void onShutdown_cancelsBothTasks() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		GangPresenceTracker tracker = new GangPresenceTracker(plugin,
		                                                      mock(GangLookupContract.class),
		                                                      mock(UserLookupContract.class),
		                                                      10,
		                                                      mock(Runnable.class));

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			BukkitTask heartbeat = mock(BukkitTask.class);
			BukkitTask release   = mock(BukkitTask.class);
			when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
					.thenReturn(heartbeat, release);
			tracker.start();

			tracker.onShutdown();

			verify(heartbeat).cancel();
			verify(release).cancel();
		}
	}
}
