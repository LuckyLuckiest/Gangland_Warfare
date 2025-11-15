package me.luckyraven.bukkit.sign.parser;

import me.luckyraven.bukkit.sign.SignType;
import me.luckyraven.bukkit.sign.validation.SignValidationException;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.ChatColor;

public abstract class AbstractSignParser implements SignParser {

	protected final SignType signType;

	protected AbstractSignParser(SignType signType) {
		this.signType = signType;
	}

	protected String cleanLine(String line) {
		return ChatUtil.replaceColorCodes(ChatColor.stripColor(line), "").trim();
	}

	protected double parsePrice(String line) throws SignValidationException {
		String cleaned = cleanLine(line).replace(SettingAddon.getMoneySymbol(), "").replace(",", "");

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
