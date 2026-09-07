package org.luckyraven.gangland.sign.validation;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.SignType;

public class ViewSignValidator extends AbstractSignValidator {

	public ViewSignValidator(SignType signType) {
		super(signType, Settings.getMoneySymbol());
	}

	@Override
	protected boolean isValidContent(String content) {
		// View signs accept any non-empty content - they're just for display.
		return !content.isEmpty();
	}

	@Override
	protected void validatePrice(String line, int lineNumber, String moneySymbol) throws SignValidationException { }

	@Override
	protected void validateAmount(String line, int lineNumber) throws SignValidationException { }
}
