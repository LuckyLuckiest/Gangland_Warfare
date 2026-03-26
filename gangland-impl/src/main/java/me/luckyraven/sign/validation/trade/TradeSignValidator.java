package me.luckyraven.sign.validation.trade;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.validation.AbstractSignValidator;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.AmmunitionManager;

import java.util.*;

public class TradeSignValidator extends AbstractSignValidator {

	private final WeaponService     weaponService;
	private final AmmunitionManager ammunitionManager;

	public TradeSignValidator(SignType signType, WeaponService weaponService, AmmunitionManager ammunitionManager) {
		super(signType, Settings.getMoneySymbol());

		this.weaponService     = weaponService;
		this.ammunitionManager = ammunitionManager;
	}

	@Override
	protected boolean isValidContent(String content) {
		// check if it is a valid weapon
		Map<UUID, Weapon> weapons = weaponService.getWeapons();

		boolean found = weapons.values()
				.stream().anyMatch(weapon -> weapon.getName().equalsIgnoreCase(content));

		if (!found) {
			Set<String> ammunitionKeys = ammunitionManager.getAmmunitionKeys();

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
