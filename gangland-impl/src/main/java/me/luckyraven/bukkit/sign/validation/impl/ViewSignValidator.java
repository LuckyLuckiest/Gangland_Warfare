package me.luckyraven.bukkit.sign.validation.impl;

import me.luckyraven.bukkit.sign.SignType;
import me.luckyraven.bukkit.sign.validation.AbstractSignValidator;
import me.luckyraven.bukkit.sign.validation.SignValidationException;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;

import java.util.Collection;

public class ViewSignValidator extends AbstractSignValidator {

	private final WeaponService   weaponService;
	private final AmmunitionAddon ammunitionAddon;

	public ViewSignValidator(SignType signType, WeaponService weaponService, AmmunitionAddon ammunitionAddon) {
		super(signType);

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
	protected void validatePrice(String line, int lineNumber) throws SignValidationException {
		// View signs don't use price, so this line can be anything
		// We'll just check it's not empty
		String cleaned = ChatUtil.replaceColorCodes(line, "").trim();
		if (cleaned.isEmpty()) {
			throw new SignValidationException("Line 3 cannot be empty (use '-' if not needed)", lineNumber, line);
		}
	}

	@Override
	protected void validateAmount(String line, int lineNumber) throws SignValidationException {
		// View signs don't use amount, so this line can be anything
		String cleaned = ChatUtil.replaceColorCodes(line, "").trim();
		if (cleaned.isEmpty()) {
			throw new SignValidationException("Line 4 cannot be empty (use '-' if not needed)", lineNumber, line);
		}
	}
}
