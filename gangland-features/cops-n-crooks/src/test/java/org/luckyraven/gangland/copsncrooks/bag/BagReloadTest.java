package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.luckyraven.gangland.copsncrooks.bag.BagFixture.bd;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Judge-fix r1: a /glw reload runs the shutdown hook (CopManager.onPreClear) and must not leave the bag dead. */
@DisplayName("Bag reload - the shutdown hook of a reload is undone by the lifecycle re-initialise")
class BagReloadTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("BagService is a BeanLifecycle and onInitialize(false) clears shuttingDown so quits spill again")
	void serviceRecoversAfterReload() {
		BagFixture fx     = new BagFixture(tempDir);
		Player     runner = fx.player("Runner");
		fx.level(runner, 2);
		fx.service.take(runner, bd("700"), "pickup");
		fx.service.settle(runner);

		fx.service.shutdown();
		assertTrue(fx.service.isShuttingDown());

		Object asBean = fx.service;
		assertTrue(asBean instanceof BeanLifecycle, "BagService must implement BeanLifecycle");
		((BeanLifecycle) asBean).onInitialize(false);

		assertFalse(fx.service.isShuttingDown(), "reload re-initialise clears the shutdown flag");
		fx.service.onQuit(runner);
		assertEquals(1, fx.spills.size(), "a quit after the reload spills again");
	}

	@Test
	@DisplayName("BagHud is a BeanLifecycle and onInitialize(false) restarts the stopped repeating task")
	void hudRestartsAfterReload() {
		JavaPlugin      plugin    = mock(JavaPlugin.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		org.bukkit.Server server  = mock(org.bukkit.Server.class);
		when(plugin.getServer()).thenReturn(server);
		when(server.getScheduler()).thenReturn(scheduler);
		when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));
		BagHud hud = new BagHud(plugin, mock(BagService.class), () -> BagSettings.DEFAULT);
		hud.start();
		hud.stop();

		Object asBean = hud;
		assertTrue(asBean instanceof BeanLifecycle, "BagHud must implement BeanLifecycle");
		((BeanLifecycle) asBean).onInitialize(false);

		verify(scheduler, times(2)).runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong());
	}
}
