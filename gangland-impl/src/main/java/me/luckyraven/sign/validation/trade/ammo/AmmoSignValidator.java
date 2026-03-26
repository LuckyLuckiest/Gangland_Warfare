package me.luckyraven.sign.validation.trade.ammo;

import me.luckyraven.file.configuration.Settings;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.validation.AbstractSignValidator;
import me.luckyraven.weapon.ammo.AmmunitionManager;

public class AmmoSignValidator extends AbstractSignValidator {

	private final AmmunitionManager ammunitionManager;

	public AmmoSignValidator(SignType signType, AmmunitionManager ammunitionManager) {
		super(signType, Settings.getMoneySymbol());

		this.ammunitionManager = ammunitionManager;
	}

	@Override
	protected boolean isValidContent(String content) {
		return ammunitionManager.getAmmunitionKeys()
				.stream()
				.anyMatch(k -> k.equalsIgnoreCase(content));
	}

}
