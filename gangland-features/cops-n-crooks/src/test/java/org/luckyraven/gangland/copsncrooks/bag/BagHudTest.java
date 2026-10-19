package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lane bag, P-BAG-10: {@link BagHud}, the repeating action-bar refresh. */
@DisplayName("BagHud - the Bag: $X action bar task")
class BagHudTest {

	private JavaPlugin      plugin;
	private BukkitScheduler scheduler;
	private BukkitTask      task;
	private BagService      service;
	private List<Player>    online;
	private BagSettings     settings = new BagSettings(true, 0.35, 5000, 15000, 4, "medium", true, 25);
	private BagHud          hud;

	@BeforeEach
	@SuppressWarnings({"unchecked", "rawtypes"})
	void setUp() {
		plugin    = mock(JavaPlugin.class);
		scheduler = mock(BukkitScheduler.class);
		task      = mock(BukkitTask.class);
		service   = mock(BagService.class);
		Server server = mock(Server.class);
		online = List.of(mock(Player.class));
		when(plugin.getServer()).thenReturn(server);
		when(server.getScheduler()).thenReturn(scheduler);
		when((Collection) server.getOnlinePlayers()).thenReturn(online);
		when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
		hud = new BagHud(plugin, service, () -> settings);
	}

	@Test
	@DisplayName("start schedules one synchronous repeating task at Hud_Interval_Ticks")
	void startSchedulesAtTheInterval() {
		hud.start();

		verify(scheduler, times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(25L), eq(25L));
	}

	@Test
	@DisplayName("the task body refreshes the HUD for the online players")
	void taskBodyRefreshesTheHud() {
		hud.start();
		ArgumentCaptor<Runnable> body = ArgumentCaptor.forClass(Runnable.class);
		verify(scheduler).runTaskTimer(eq(plugin), body.capture(), anyLong(), anyLong());

		body.getValue().run();

		verify(service).refreshHud(online);
	}

	@Test
	@DisplayName("stop cancels the task; a second stop, or a stop before start, is harmless")
	void stopCancels() {
		hud.stop();
		hud.start();

		hud.stop();
		hud.stop();

		verify(task, times(1)).cancel();
	}

	@Test
	@DisplayName("start twice cancels the first task: one task at a time")
	void restartCancelsTheFirst() {
		hud.start();

		hud.start();

		verify(task, times(1)).cancel();
		verify(scheduler, times(2)).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong());
	}

	@Test
	@DisplayName("Hud_Enable false schedules nothing")
	void disabledSchedulesNothing() {
		settings = new BagSettings(true, 0.35, 5000, 15000, 4, "medium", false, 25);

		hud.start();

		verify(scheduler, never()).runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong());
	}
}
