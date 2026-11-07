package org.luckyraven.gangland.sign.validation;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.type.BountySign;

import java.util.Arrays;

public class BountySignValidator extends AbstractSignValidator {

	public BountySignValidator(SignType signType) {
		super(signType, Settings.getMoneySymbol());
	}

	@Override
	protected boolean isValidContent(String content) {
		return Arrays.stream(BountySign.BountyType.values()).anyMatch(type -> type.name().equalsIgnoreCase(content));
	}

	@Override
	protected void validatePrice(String line, int lineNumber, String moneySymbol) throws SignValidationException { }

	@Override
	protected void validateAmount(String line, int lineNumber) throws SignValidationException { }
}
