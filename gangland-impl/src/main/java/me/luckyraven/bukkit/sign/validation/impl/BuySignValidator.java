package me.luckyraven.bukkit.sign.validation.impl;

import me.luckyraven.bukkit.sign.SignType;
import me.luckyraven.bukkit.sign.validation.AbstractSignValidator;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BuySignValidator extends AbstractSignValidator {

	private final WeaponService   weaponService;
	private final AmmunitionAddon ammunitionAddon;

	public BuySignValidator(SignType signType, WeaponService weaponService, AmmunitionAddon ammunitionAddon) {
		super(signType);

		this.weaponService   = weaponService;
		this.ammunitionAddon = ammunitionAddon;
	}

	@Override
	protected boolean isValidContent(String content) {
		// check if it is a valid weapon

		Map<UUID, Weapon> weapons = weaponService.getWeapons();

		boolean found = weapons.values()
				.stream().anyMatch(weapon -> weapon.getName().equalsIgnoreCase(content));

		if (!found) {
			Set<String> ammunitionKeys = ammunitionAddon.getAmmunitionKeys();

			found = ammunitionKeys.stream().anyMatch(ammo -> ammo.equalsIgnoreCase(content));
		}

		return found;
	}
}
