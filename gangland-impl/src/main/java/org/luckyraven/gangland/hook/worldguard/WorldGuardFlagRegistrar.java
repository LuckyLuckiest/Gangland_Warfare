package org.luckyraven.gangland.hook.worldguard;

import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import lombok.CustomLog;

/** Names WorldGuard types; only ever touched through {@link WorldGuardHooks} once the plugin is known to be present. */
@CustomLog
final class WorldGuardFlagRegistrar {

	static final String FLAG_NAME = "glw-gang-pvp";

	private WorldGuardFlagRegistrar() {
	}

	static void register(FlagRegistry registry) {
		try {
			registry.register(new StateFlag(FLAG_NAME, false));
		} catch (FlagConflictException exception) {
			log.warn("WorldGuard flag {} is already registered by another plugin; region friendly-fire is off.", FLAG_NAME);
		}
	}
}
