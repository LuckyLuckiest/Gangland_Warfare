package org.luckyraven.gangland.gadget.listener.car;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.bartizan.api.weapon.MeleeWeapon;
import org.luckyraven.bartizan.api.weapon.Weapon;
import org.luckyraven.bartizan.api.weapon.WeaponCatalog;

/**
 * Static-only Bartizan melee-weapon lookup (B5, WS7 G5): no {@code @Bean}, no field, no constructor parameter, not
 * in any {@code @AutowireTarget}. This class — and its Bartizan-typed method signatures — is only linked when a
 * static call inside it actually executes; the only call sites are {@code CarDamageListener.onVehicleDamage},
 * each behind an explicit {@code Settings.isBartizanAvailable()} check, so a Bartizan-less server never loads this
 * class at all.
 */
public final class CarMeleeWeaponLookup {

	private CarMeleeWeaponLookup() {
	}

	/** True if {@code player} is holding a Bartizan weapon item right now — gates the sneak+left-click pickup path. */
	public static boolean isHoldingWeapon(Player player) {
		WeaponCatalog weapons = weapons();
		return weapons != null && weapons.isWeapon(player.getInventory().getItemInMainHand());
	}

	/** The melee weapon's configured damage if {@code player} holds one, otherwise {@code fallback} (vanilla punch). */
	public static int resolveMeleeDamage(Player player, int fallback) {
		WeaponCatalog weapons = weapons();
		if (weapons == null) return fallback;

		ItemStack item   = player.getInventory().getItemInMainHand();
		Weapon    weapon = weapons.validateAndGetWeapon(player, item);
		if (weapon instanceof MeleeWeapon melee) {
			return (int) Math.ceil(melee.getMeleeData().getDamage());
		}
		return fallback;
	}

	@Nullable
	private static WeaponCatalog weapons() {
		RegisteredServiceProvider<BartizanApi> rsp = Bukkit.getServicesManager().getRegistration(BartizanApi.class);
		return rsp == null ? null : rsp.getProvider().weapons();
	}
}
