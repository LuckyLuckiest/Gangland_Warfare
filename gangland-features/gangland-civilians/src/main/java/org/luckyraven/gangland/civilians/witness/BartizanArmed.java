package org.luckyraven.gangland.civilians.witness;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.gangland.file.configuration.Settings;

import java.util.function.Predicate;

/**
 * Whether a player holds a Bartizan weapon. Resolves {@code BartizanApi} fresh from the ServicesManager on every
 * call, never cached; Bartizan absent means never armed.
 *
 * @since 0.20.0
 */
public final class BartizanArmed implements Predicate<Player> {

	@Override
	public boolean test(Player player) {
		if (!Settings.isBartizanAvailable()) return false;

		RegisteredServiceProvider<BartizanApi> registration = Bukkit.getServicesManager()
				.getRegistration(BartizanApi.class);
		if (registration == null) return false;

		BartizanApi api = registration.getProvider();
		return api != null && api.getHeldWeapon(player) != null;
	}
}
