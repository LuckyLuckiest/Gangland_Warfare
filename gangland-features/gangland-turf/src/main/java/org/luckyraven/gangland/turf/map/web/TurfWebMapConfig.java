package org.luckyraven.gangland.turf.map.web;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMapSettings;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.bean.Configuration;

/**
 * W3-W's beans (the dynmap turf layer). Nothing here names a dynmap type: the layer is a type-free
 * {@link TurfWebLayer}, a no-op unless dynmap is installed.
 */
@Configuration
public final class TurfWebMapConfig {

	/**
	 * Seconds between full reconciles. Turf creation fires no event, so this is how a new turf reaches the map.
	 */
	private static final long RECONCILE_TICKS = 20L * 30L;

	@Bean
	public TurfWebLayer turfWebLayer(JavaPlugin plugin, TurfManager turfs, GangLookupContract gangs,
	                                 TurfMapSettings settings) {
		TurfWebLayer layer = TurfWebLayers.create(plugin, turfs, gangs, settings);
		if (layer instanceof TurfWebLayers.NoOp) {
			return layer;
		}
		return new Managed(plugin, Bukkit.getScheduler(), layer);
	}

	/**
	 * Draws once the server finished enabling (turfs loaded, dynmap up), reconciles periodically, and removes the
	 * marker set on shutdown/reload so nothing leaks.
	 */
	static final class Managed implements TurfWebLayer, BeanLifecycle {

		private final JavaPlugin    plugin;
		private final BukkitScheduler scheduler;
		private final TurfWebLayer  delegate;
		private BukkitTask          first;
		private BukkitTask          reconcile;

		Managed(JavaPlugin plugin, BukkitScheduler scheduler, TurfWebLayer delegate) {
			this.plugin    = plugin;
			this.scheduler = scheduler;
			this.delegate  = delegate;
		}

		/**
		 * Keystone calls this right after registration (first boot) and again after onClear on /glw reload.
		 */
		@Override
		public void onInitialize(boolean firstLoad) {
			cancelTasks();
			first     = scheduler.runTask(plugin, delegate::rebuild);
			reconcile = scheduler.runTaskTimer(plugin, delegate::rebuild, RECONCILE_TICKS, RECONCILE_TICKS);
		}

		private void cancelTasks() {
			if (first != null) {
				first.cancel();
				first = null;
			}
			if (reconcile != null) {
				reconcile.cancel();
				reconcile = null;
			}
		}

		@Override
		public void rebuild() {
			delegate.rebuild();
		}

		@Override
		public void sync(Turf turf) {
			delegate.sync(turf);
		}

		@Override
		public void remove(int turfId) {
			delegate.remove(turfId);
		}

		@Override
		public void shutdown() {
			cancelTasks();
			delegate.shutdown();
		}

		@Override
		public void onClear() {
			shutdown();
		}

		@Override
		public void onShutdown() {
			shutdown();
		}
	}
}
