package me.luckyraven.sign.validation;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.type.BountySign;

public class BountySignValidator extends AbstractSignValidator {

	public BountySignValidator(SignType signType) {
		super(signType, SettingAddon.getMoneySymbol());
	}

	@Override
	protected boolean isValidContent(String content) {
		String view  = BountySign.BountyType.VIEW.name().toLowerCase();
		String clear = BountySign.BountyType.CLEAR.name().toLowerCase();

		return content.equalsIgnoreCase(view) || content.equalsIgnoreCase(clear);
	}

	@Override
	protected void validatePrice(String line, int lineNumber, String moneySymbol) throws SignValidationException { }

	@Override
	protected void validateAmount(String line, int lineNumber) throws SignValidationException { }
}
