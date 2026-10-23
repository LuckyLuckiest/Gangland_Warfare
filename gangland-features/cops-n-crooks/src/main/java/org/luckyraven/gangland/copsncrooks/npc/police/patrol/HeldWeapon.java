package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.luckyraven.bartizan.api.BartizanApi;

/**
 * Whether a player holds a Bartizan weapon. Bartizan is resolved from the {@link ServicesManager} on every call (it
 * may enable after this module, or not be installed); absent means "not holding".
 */
public final class HeldWeapon {

	private HeldWeapon() {
	}

	public static boolean holding(ServicesManager services, Player player) {
		RegisteredServiceProvider<BartizanApi> registration = services.getRegistration(BartizanApi.class);
		if (registration == null) {
			return false;
		}

		BartizanApi api = registration.getProvider();
		if (api == null) {
			return false;
		}

		ItemStack held = player.getInventory().getItemInMainHand();
		return held != null && api.weapons().isWeapon(held);
	}

	/** Production entry: {@code Bukkit.getServicesManager()}. */
	public static boolean holding(Player player) {
		return holding(Bukkit.getServicesManager(), player);
	}
}
