package me.luckyraven.sign.validation;

import me.luckyraven.sign.SignType;
import me.luckyraven.util.ChatUtil;

public class WantedSignValidator extends AbstractSignValidator {

	public WantedSignValidator(SignType signType) {
		super(signType);
	}

	@Override
	protected boolean isValidContent(String content) {
		return content.equalsIgnoreCase("add") || content.equalsIgnoreCase("remove") ||
			   content.equalsIgnoreCase("clear");
	}

	@Override
	protected void performCustomValidation(String[] lines) throws SignValidationException {
		super.performCustomValidation(lines);
	}

	@Override
	protected void validatePrice(String line, int lineNumber) throws SignValidationException {
		String cleaned = ChatUtil.replaceColorCodes(line, "");

		if (cleaned.isEmpty()) {
			throw new SignValidationException("Stars line cannot be empty", lineNumber, line);
		}
	}

}
