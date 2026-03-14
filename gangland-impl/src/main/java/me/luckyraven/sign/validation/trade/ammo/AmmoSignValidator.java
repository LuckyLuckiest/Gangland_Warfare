package me.luckyraven.sign.validation.trade.ammo;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.validation.AbstractSignValidator;
import me.luckyraven.weapon.configuration.AmmunitionAddon;

public class AmmoSignValidator extends AbstractSignValidator {

	private final AmmunitionAddon ammunitionAddon;

	public AmmoSignValidator(SignType signType, AmmunitionAddon ammunitionAddon) {
		super(signType, SettingAddon.getMoneySymbol());

		this.ammunitionAddon = ammunitionAddon;
	}

	@Override
	protected boolean isValidContent(String content) {
		return ammunitionAddon.getAmmunitionKeys()
				.stream()
				.anyMatch(k -> k.equalsIgnoreCase(content));
	}

}
