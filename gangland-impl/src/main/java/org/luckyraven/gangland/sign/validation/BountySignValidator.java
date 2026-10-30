package org.luckyraven.gangland.sign.validation;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.type.BountySign;

public class BountySignValidator extends AbstractSignValidator {

	public BountySignValidator(SignType signType) {
		super(signType, Settings.getMoneySymbol());
	}

	@Override
	protected boolean isValidContent(String content) {
		for (BountySign.BountyType type : BountySign.BountyType.values()) {
			if (type.name().equalsIgnoreCase(content)) return true;
		}

		return false;
	}

	@Override
	protected void validatePrice(String line, int lineNumber, String moneySymbol) throws SignValidationException { }

	@Override
	protected void validateAmount(String line, int lineNumber) throws SignValidationException { }
}
