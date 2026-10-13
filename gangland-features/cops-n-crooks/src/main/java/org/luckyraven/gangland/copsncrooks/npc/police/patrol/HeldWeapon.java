package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;

/**
 * Whether a player holds a Bartizan weapon. Bartizan is resolved from the {@link ServicesManager} on every call (it
 * may enable after this module, or not be installed); absent means "not holding".
 */
public final class HeldWeapon {

	private HeldWeapon() {
	}

	public static boolean holding(ServicesManager services, Player player) {
		// TDD stub: neutral
		return false;
	}

	/** Production entry: {@code Bukkit.getServicesManager()}. */
	public static boolean holding(Player player) {
		return holding(Bukkit.getServicesManager(), player);
	}
}
