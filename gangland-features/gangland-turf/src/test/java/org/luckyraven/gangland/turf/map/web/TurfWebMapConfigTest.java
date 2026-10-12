package org.luckyraven.gangland.turf.map.web;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TurfWebMapConfigTest {

	@Test
	void onClearThenInitialize_schedulesRebuildAgain() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		BukkitTask first = mock(BukkitTask.class);
		BukkitTask timer = mock(BukkitTask.class);
		when(scheduler.runTask(any(JavaPlugin.class), any(Runnable.class))).thenReturn(first);
		when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong())).thenReturn(timer);

		TurfWebMapConfig.Managed managed = new TurfWebMapConfig.Managed(plugin, scheduler, mock(TurfWebLayer.class));
		managed.onInitialize(true);
		managed.onClear();
		managed.onInitialize(false);

		verify(scheduler, times(2)).runTask(any(JavaPlugin.class), any(Runnable.class));
		verify(scheduler, times(2)).runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong());
		verify(timer, times(1)).cancel();
	}
}
