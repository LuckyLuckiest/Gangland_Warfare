package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.luckyraven.bartizan.api.BartizanApi;

/**
 * The ONLY class in this module that names Bartizan types. Never cached: BartizanApi is resolved on every call.
 */
public final class BartizanAim {

	private BartizanAim() {
	}

	public static boolean weaponHeld(ServicesManager services, Player player) {
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

	/** Production entry: {@code Bukkit.getServicesManager()}. Call only after the Bartizan plugin is known present. */
	public static boolean weaponHeld(Player player) {
		return weaponHeld(Bukkit.getServicesManager(), player);
	}

}
