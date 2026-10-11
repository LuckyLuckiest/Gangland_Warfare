package org.luckyraven.gangland.hook.worldguard;

import com.sk89q.worldguard.WorldGuard;
import org.bukkit.plugin.PluginManager;

/** Type-free entry point: callers never name a WorldGuard class, so a server without it never loads one. */
public final class WorldGuardHooks {

	private WorldGuardHooks() {
	}

	public static boolean present(PluginManager pluginManager) {
		return pluginManager.getPlugin("WorldGuard") != null;
	}

	/** Call from {@code onLoad} only, and only when {@link #present} is true. */
	public static void registerFlag() {
		WorldGuardFlagRegistrar.register(WorldGuard.getInstance().getFlagRegistry());
	}
}
