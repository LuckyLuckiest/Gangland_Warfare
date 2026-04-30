package org.luckyraven.gangland.sign.validation;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;

import java.util.Collection;

public class ViewSignValidator extends AbstractSignValidator {

	private final WeaponService     weaponService;
	private final AmmunitionManager ammunitionManager;

	public ViewSignValidator(SignType signType, WeaponService weaponService, AmmunitionManager ammunitionManager) {
		super(signType, Settings.getMoneySymbol());

		this.weaponService     = weaponService;
		this.ammunitionManager = ammunitionManager;
	}

	@Override
	protected boolean isValidContent(String content) {
		// View signs accept any content - they're just for display,
		// But we prefer weapons and ammunition
		if (content.isEmpty()) {
			return false;
		}

		// Check if it's a weapon
		Collection<Weapon> values = weaponService.getWeapons().values();
		boolean isWeapon = values.stream()
				.anyMatch(weapon -> weapon.getName().equalsIgnoreCase(content) ||
				                    weapon.getDisplayName().equalsIgnoreCase(content));

		if (isWeapon) {
			return true;
		}

		// Check if it's ammunition
		boolean isAmmo = ammunitionManager.getAmmunitionKeys()
				.stream().anyMatch(ammo -> ammo.equalsIgnoreCase(content));

		if (isAmmo) {
			return true;
		}

		// Accept any other item name
		return true;
	}

	@Override
	protected void validatePrice(String line, int lineNumber, String moneySymbol) throws SignValidationException { }

	@Override
	protected void validateAmount(String line, int lineNumber) throws SignValidationException { }
}
