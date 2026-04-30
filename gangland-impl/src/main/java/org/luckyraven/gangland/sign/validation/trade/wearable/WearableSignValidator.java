package org.luckyraven.gangland.sign.validation.trade.wearable;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.validation.AbstractSignValidator;
import org.luckyraven.gangland.weapon.wearable.WearableService;

public class WearableSignValidator extends AbstractSignValidator {

	private final WearableService wearableService;

	public WearableSignValidator(SignType signType, WearableService wearableService) {
		super(signType, Settings.getMoneySymbol());

		this.wearableService = wearableService;
	}

	@Override
	protected boolean isValidContent(String content) {
		return wearableService.getWearable(content) != null;
	}

}
