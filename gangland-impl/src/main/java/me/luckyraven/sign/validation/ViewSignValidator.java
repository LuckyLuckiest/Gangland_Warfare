package me.luckyraven.sign.validation;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.SignType;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;

import java.util.Collection;

public class ViewSignValidator extends AbstractSignValidator {

	private final WeaponService   weaponService;
	private final AmmunitionAddon ammunitionAddon;

	public ViewSignValidator(SignType signType, WeaponService weaponService, AmmunitionAddon ammunitionAddon) {
		super(signType, SettingAddon.getMoneySymbol());

		this.weaponService   = weaponService;
		this.ammunitionAddon = ammunitionAddon;
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
		boolean isAmmo = ammunitionAddon.getAmmunitionKeys()
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
