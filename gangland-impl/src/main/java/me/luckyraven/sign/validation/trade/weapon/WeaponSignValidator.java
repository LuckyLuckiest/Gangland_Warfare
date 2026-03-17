package me.luckyraven.sign.validation.trade.weapon;

import me.luckyraven.file.configuration.Settings;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.validation.AbstractSignValidator;
import me.luckyraven.weapon.WeaponService;

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
