package me.luckyraven.sign.validation;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.type.WantedSign;
import me.luckyraven.util.ChatUtil;

public class WantedSignValidator extends AbstractSignValidator {

	public WantedSignValidator(SignType signType) {
		super(signType, SettingAddon.getMoneySymbol());
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

		try {
			int stars = Integer.parseInt(starsLine);
			if (stars <= 0) {
				throw new SignValidationException("Star count must be positive", 3, starsLine);
			}
		} catch (NumberFormatException exception) {
			throw new SignValidationException("Star count must be a valid number", 3, starsLine);
		}

		String priceLine = ChatUtil.replaceColorCodes(lines[3], "");
		if (priceLine.isEmpty()) {
			return;
		}

		try {
			int price = Integer.parseInt(priceLine);
			if (price <= 0) {
				throw new SignValidationException("Price must be positive", 4, priceLine);
			}
		} catch (NumberFormatException exception) {
			throw new SignValidationException("Price must be a valid number", 4, priceLine);
		}
	}

	@Override
	protected void validatePrice(String line, int lineNumber, String moneySymbol) throws SignValidationException { }

	@Override
	protected void validateAmount(String line, int lineNumber) throws SignValidationException { }
}
