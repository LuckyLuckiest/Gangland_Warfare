package org.luckyraven.gangland.turf.map.web;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMapSettings;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;

/**
 * Type-free factory. The only place that decides whether {@link DynmapTurfLayer} (the one class naming dynmap types)
 * is ever loaded: it is not touched unless {@code Web.Enabled} is on and the dynmap plugin is present.
 */
public final class TurfWebLayers {

	private TurfWebLayers() {
	}

	public static TurfWebLayer create(JavaPlugin plugin, TurfManager turfs, GangLookupContract gangs,
	                                  TurfMapSettings settings) {
		return create(plugin, turfs, gangs, settings, Bukkit.getPluginManager().getPlugin("dynmap"));
	}

	static TurfWebLayer create(@Nullable JavaPlugin plugin, TurfManager turfs, GangLookupContract gangs,
	                           TurfMapSettings settings, @Nullable Plugin dynmap) {
		if (!settings.isWebEnabled() || dynmap == null) {
			return new NoOp();
		}
		return DynmapTurfLayer.create(plugin, dynmap, turfs, gangs, settings);
	}

	/**
	 * Used when dynmap is absent or the layer is switched off.
	 */
	static final class NoOp implements TurfWebLayer {

		@Override
		public void rebuild() {
		}

		@Override
		public void sync(Turf turf) {
		}

		@Override
		public void remove(int turfId) {
		}

		@Override
		public void shutdown() {
		}
	}
}
