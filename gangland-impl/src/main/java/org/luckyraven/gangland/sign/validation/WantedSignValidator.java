package org.luckyraven.gangland.sign.validation;

import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.type.WantedSign;

public class WantedSignValidator extends AbstractSignValidator {

	public WantedSignValidator(SignType signType) {
		super(signType, Settings.getMoneySymbol());
	}

	@Override
	protected boolean isValidContent(String content) {
		String increase = WantedSign.WantedType.INCREASE.name().toLowerCase();
		String remove   = WantedSign.WantedType.REMOVE.name().toLowerCase();
		String clear    = WantedSign.WantedType.CLEAR.name().toLowerCase();

		return content.equalsIgnoreCase(increase) || content.equalsIgnoreCase(remove) ||
		       content.equalsIgnoreCase(clear);
	}

	@Override
	protected void performCustomValidation(String[] lines) throws SignValidationException {
		super.performCustomValidation(lines);

		// Line 2 must contain valid operation
		String content = ChatUtil.replaceColorCodes(lines[1], "");
		if (content.isEmpty() || !isValidContent(content)) {
			throw new SignValidationException("Line 2 must be 'increase', 'remove', or 'clear'", 2, content);
		}

		// If operation is not "clear", line 3 must have a number (amount of stars)
		if (content.equalsIgnoreCase(WantedSign.WantedType.CLEAR.name().toLowerCase())) return;

		String starsLine = ChatUtil.replaceColorCodes(lines[2], "");
		if (starsLine == null || starsLine.isEmpty()) {
			throw new SignValidationException("Line 3 must specify number of stars", 3, starsLine);
		}

		requirePositiveInt(starsLine, 3, "Star count must be positive", "Star count must be a valid number");

		String priceLine = ChatUtil.replaceColorCodes(lines[3], "");
		if (priceLine.isEmpty()) {
			return;
		}

		requirePositiveInt(priceLine, 4, "Price must be positive", "Price must be a valid number");
	}

	private static void requirePositiveInt(String text, int lineNumber, String positiveMessage, String numberMessage)
			throws SignValidationException {
		try {
			int value = Integer.parseInt(text);
			if (value <= 0) {
				throw new SignValidationException(positiveMessage, lineNumber, text);
			}
		} catch (NumberFormatException exception) {
			throw new SignValidationException(numberMessage, lineNumber, text);
		}
	}

	@Override
	protected void validatePrice(String line, int lineNumber, String moneySymbol) throws SignValidationException { }

	@Override
	protected void validateAmount(String line, int lineNumber) throws SignValidationException { }
}
