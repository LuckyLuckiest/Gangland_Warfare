package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;

/**
 * The ONLY class in this module that names Bartizan types. Never cached: BartizanApi is resolved on every call.
 */
public final class BartizanAim {

	private BartizanAim() {
	}

	public static boolean weaponHeld(ServicesManager services, Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Production entry: {@code Bukkit.getServicesManager()}. Call only after the Bartizan plugin is known present. */
	public static boolean weaponHeld(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

}
