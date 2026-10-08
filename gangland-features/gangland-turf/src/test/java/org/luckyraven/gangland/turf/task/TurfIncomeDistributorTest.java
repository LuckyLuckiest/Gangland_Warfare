package org.luckyraven.gangland.turf.task;

import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Pins TF-10 for the income task (orphan branch fires ORPHANED and deposits nothing) and TF-07 (shutdown stops the
 * scheduled task).
 */
@DisplayName("TurfIncomeDistributor - orphan release fires an event; shutdown stops the timer (TF-10, TF-07)")
class TurfIncomeDistributorTest {

	private static final long INTERVAL = 1200L;

	private JavaPlugin        plugin;
	private TurfManager       turfs;
	private FakeGangLookup    gangs;
	private ActiveBuffManager buffs;

	@BeforeEach
	void setUp() {
		plugin = mock(JavaPlugin.class);
		turfs  = new TurfManager(mock(TurfRepositoryContract.class));
		gangs  = new FakeGangLookup();
		buffs  = mock(ActiveBuffManager.class);
	}

	private TurfIncomeDistributor distributor() {
		return new TurfIncomeDistributor(plugin, turfs, gangs, buffs, new TurfOwnership(turfs), INTERVAL);
	}

	@Test
	@DisplayName("a turf whose owner gang is missing is released with cause ORPHANED and no income is deposited")
	void distribute_missingGang_firesOrphanedAndDepositsNothing() {
		Turf turf = new Turf(1, "T", new CuboidRegion("world", 0, 0, 10, 10), 99, BigDecimal.TEN, 0L, 0L);
		turfs.create(turf);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			TurfIncomeDistributor distributor = distributor();
			distributor.start();

			ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
			verify(bukkit.scheduler()).runTaskTimer(any(Plugin.class), task.capture(), anyLong(), anyLong());
			task.getValue().run();

			assertNull(turf.getOwnerGangId());
			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), times(1)).callEvent(captor.capture());
			assertEquals(TurfOwnerChangedEvent.Cause.ORPHANED,
			             ((TurfOwnerChangedEvent) captor.getValue()).getCause());
		}
	}

	@Test
	@DisplayName("onShutdown cancels the scheduled income task started by start()")
	void onShutdown_cancelsScheduledTask() {
		BukkitTask scheduled = mock(BukkitTask.class);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
					.thenReturn(scheduled);
			TurfIncomeDistributor distributor = distributor();
			distributor.start();

			distributor.onShutdown();

			verify(scheduled).cancel();
		}
	}
}
