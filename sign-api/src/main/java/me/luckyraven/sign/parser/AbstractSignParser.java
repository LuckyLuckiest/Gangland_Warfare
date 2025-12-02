package me.luckyraven.sign.parser;

import me.luckyraven.sign.SignType;
import me.luckyraven.sign.validation.SignValidationException;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.ChatColor;

public abstract class AbstractSignParser implements SignParser {

	protected final SignType signType;

	protected AbstractSignParser(SignType signType) {
		this.signType = signType;
	}

	protected String cleanLine(String line) {
		return ChatUtil.replaceColorCodes(ChatColor.stripColor(line), "").trim();
	}

	protected double parsePrice(String line, String moneySymbol) throws SignValidationException {
		String cleaned = cleanLine(line).replace(moneySymbol, "").replace(",", "");

		try {
			return Double.parseDouble(cleaned);
		} catch (NumberFormatException exception) {
			throw new SignValidationException("Invalid parsed price: '" + cleaned + "'");
		}
	}

	protected int parseAmount(String line) throws SignValidationException {
		String cleaned = cleanLine(line).replace(",", "");

		try {
			return Integer.parseInt(cleaned);
		} catch (NumberFormatException exception) {
			throw new SignValidationException("Invalid parsed amount: '" + cleaned + "'");
		}
	}

	protected String parseContent(String line) {
		return cleanLine(line);
	}

}
