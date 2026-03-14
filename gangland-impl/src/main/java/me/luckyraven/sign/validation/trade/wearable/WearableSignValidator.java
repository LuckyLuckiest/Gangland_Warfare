package me.luckyraven.sign.validation.trade.wearable;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.validation.AbstractSignValidator;
import me.luckyraven.weapon.wearable.WearableService;

public class WearableSignValidator extends AbstractSignValidator {

	private final WearableService wearableService;

	public WearableSignValidator(SignType signType, WearableService wearableService) {
		super(signType, SettingAddon.getMoneySymbol());

		this.wearableService = wearableService;
	}

	@Override
	protected boolean isValidContent(String content) {
		return wearableService.getWearable(content) != null;
	}

}
