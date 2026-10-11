package org.luckyraven.gangland.turf.map.web;

import org.bukkit.Bukkit;
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
		return new Managed(plugin, layer);
	}

	/**
	 * Draws once the server finished enabling (turfs loaded, dynmap up), reconciles periodically, and removes the
	 * marker set on shutdown/reload so nothing leaks.
	 */
	private static final class Managed implements TurfWebLayer, BeanLifecycle {

		private final TurfWebLayer delegate;
		private final BukkitTask   first;
		private final BukkitTask   reconcile;

		Managed(JavaPlugin plugin, TurfWebLayer delegate) {
			this.delegate  = delegate;
			this.first     = Bukkit.getScheduler().runTask(plugin, delegate::rebuild);
			this.reconcile = Bukkit.getScheduler()
			                       .runTaskTimer(plugin, delegate::rebuild, RECONCILE_TICKS, RECONCILE_TICKS);
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
			first.cancel();
			reconcile.cancel();
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
