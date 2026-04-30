package org.luckyraven.gangland.sign.validation.trade.weapon;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.validation.AbstractSignValidator;
import org.luckyraven.gangland.weapon.WeaponService;

public class WeaponSignValidator extends AbstractSignValidator {

	private final WeaponService weaponService;

	public WeaponSignValidator(SignType signType, WeaponService weaponService) {
		super(signType, Settings.getMoneySymbol());

		this.weaponService = weaponService;
	}

	@Override
	protected boolean isValidContent(String content) {
		return weaponService.getWeapons().values()
				.stream()
				.anyMatch(w -> w.getName().equalsIgnoreCase(content));
	}

}
