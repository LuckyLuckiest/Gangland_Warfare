package org.luckyraven.gangland.sign.validation.trade.ammo;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.validation.AbstractSignValidator;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;

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
