package org.luckyraven.gangland.mail;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins GR-11: the expiry sweep is a SYNCHRONOUS main-thread timer (it mutates mail that commands touch on the main
 * thread), starts once, and is cancelled on shutdown.
 */
@DisplayName("MailExpirySweep - main-thread sweep lifecycle (GR-11)")
class MailExpirySweepTest {

	private BukkitStatics   statics;
	private BukkitScheduler scheduler;
	private JavaPlugin      plugin;
	private BukkitTask      task;
	private MailManager     manager;

	@BeforeEach
	void setUp() {
		statics   = BukkitStatics.install();
		scheduler = statics.scheduler();
		plugin    = mock(JavaPlugin.class);
		task      = mock(BukkitTask.class);
		when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);

		manager = new MailManager(new FakeMailRepositoryContract());
		manager.initialize();
	}

	@AfterEach
	void tearDown() {
		statics.close();
	}

	@Test
	@DisplayName("onInitialize schedules the sweep with the synchronous scheduler, never the async one")
	void onInitialize_schedulesSyncTimer() {
		MailExpirySweep sweep = new MailExpirySweep(plugin, manager);
		sweep.onInitialize(true);

		verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L));
		verify(scheduler, never()).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
	}

	@Test
	@DisplayName("onShutdown cancels the scheduled task")
	void onShutdown_stopsTimer() {
		MailExpirySweep sweep = new MailExpirySweep(plugin, manager);
		sweep.onInitialize(true);
		sweep.onShutdown();

		verify(task).cancel();
	}

	@Test
	@DisplayName("starting twice schedules once")
	void start_twice_schedulesOnce() {
		MailExpirySweep sweep = new MailExpirySweep(plugin, manager);
		sweep.onInitialize(true);
		sweep.onInitialize(true);

		verify(scheduler, times(1)).runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong());
	}

}
