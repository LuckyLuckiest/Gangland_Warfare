package me.luckyraven.sign.validation;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.SignType;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;

import java.util.*;

public class TradeSignValidator extends AbstractSignValidator {

	private final WeaponService   weaponService;
	private final AmmunitionAddon ammunitionAddon;

	public TradeSignValidator(SignType signType, WeaponService weaponService, AmmunitionAddon ammunitionAddon) {
		super(signType, SettingAddon.getMoneySymbol());

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

		if (!found) {
			XMaterial[] values = XMaterial.values();

			found = Arrays.stream(values)
						  .map(XMaterial::get)
						  .filter(Objects::nonNull)
						  .anyMatch(material -> material.name().equalsIgnoreCase(content));
		}

		return found;
	}

}
