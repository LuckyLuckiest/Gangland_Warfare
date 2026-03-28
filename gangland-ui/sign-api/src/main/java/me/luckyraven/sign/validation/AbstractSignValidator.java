package me.luckyraven.sign.validation;

import me.luckyraven.sign.SignType;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.ChatColor;

public abstract class AbstractSignValidator implements SignValidator {

	protected final SignType signType;
	private final   String   moneySymbol;

	protected AbstractSignValidator(SignType signType, String moneySymbol) {
		this.signType    = signType;
		this.moneySymbol = moneySymbol;
	}

	/**
	 * Validate the content string (line 2)
	 *
	 * @param content The cleaned content string
	 *
	 * @return true if valid
	 */
	protected abstract boolean isValidContent(String content);

	@Override
	public void validate(String[] lines) throws SignValidationException {
		if (lines == null || lines.length < 4) {
			throw new SignValidationException("The sign must have 4 lines!");
		}

		validateSignType(lines[0], 0);
		validateContent(lines[1], 1);
		validatePrice(lines[2], 2, moneySymbol);
		validateAmount(lines[3], 3);

		performCustomValidation(lines);
	}

	@Override
	public SignType getSignType() {
		return signType;
	}

	protected void validateSignType(String line, int lineNumber) throws SignValidationException {
		String cleaned = ChatUtil.replaceColorCodes(ChatColor.stripColor(line), "");

		boolean matchTyped     = cleaned.equalsIgnoreCase(signType.typed());
		boolean matchGenerated = cleaned.equalsIgnoreCase(signType.generated());

		if (!matchTyped && !matchGenerated) {
			throw new SignValidationException(
					"Invalid sign type. Expected: " + signType.typed() + " or " + signType.generated(), lineNumber,
					line);
		}
	}

	protected void validateContent(String line, int lineNumber) throws SignValidationException {
		String cleaned = ChatUtil.replaceColorCodes(line, "").trim();

		if (cleaned.isEmpty()) {
			throw new SignValidationException("Content line cannot be empty", lineNumber, line);
		}

		if (!isValidContent(cleaned)) {
			throw new SignValidationException("Invalid content: " + cleaned, lineNumber, line);
		}
	}

	protected void validatePrice(String line, int lineNumber, String moneySymbol) throws SignValidationException {
		String cleaned = ChatUtil.replaceColorCodes(line, "").trim();
		cleaned = cleaned.replace(moneySymbol, "").replace(",", "");

		if (cleaned.isEmpty()) {
			throw new SignValidationException("Price cannot be empty", lineNumber, line);
		}

		try {
			double price = Double.parseDouble(cleaned);

			if (price < 0) {
				throw new SignValidationException("Price cannot be negative", lineNumber, line);
			}

			if (price > getMaxPrice()) {
				throw new SignValidationException("Price exceeds maximum: " + getMaxPrice(), lineNumber, line);
			}
		} catch (NumberFormatException e) {
			throw new SignValidationException("Price must be a valid number", lineNumber, line);
		}

		if (cleaned.length() > 8) {
			throw new SignValidationException("Price text too long (max 8 chars)", lineNumber, line);
		}
	}

	protected void validateAmount(String line, int lineNumber) throws SignValidationException {
		String cleaned = ChatUtil.replaceColorCodes(line, "").trim().replace(",", "");

		if (cleaned.isEmpty()) {
			throw new SignValidationException("Amount cannot be empty", lineNumber, line);
		}

		try {
			int amount = Integer.parseInt(cleaned);

			if (amount <= 0) {
				throw new SignValidationException("Amount must be positive", lineNumber, line);
			}

			if (amount > getMaxAmount()) {
				throw new SignValidationException("Amount exceeds maximum: " + getMaxAmount(), lineNumber, line);
			}
		} catch (NumberFormatException e) {
			throw new SignValidationException("Amount must be a valid integer", lineNumber, line);
		}

		if (cleaned.length() > 8) {
			throw new SignValidationException("Amount text too long (max 8 chars)", lineNumber, line);
		}
	}

	/**
	 * Perform any custom validation specific to this sign type
	 */
	protected void performCustomValidation(String[] lines) throws SignValidationException {
		// Override in subclasses if needed
	}

	protected double getMaxPrice() {
		return 99999999.99;
	}

	protected int getMaxAmount() {
		return 99999999;
	}
}
