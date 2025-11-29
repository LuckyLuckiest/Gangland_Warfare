package me.luckyraven.sign.parser;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.model.WantedParsedSign;
import me.luckyraven.sign.validation.SignValidationException;
import org.bukkit.Location;

public class WantedParser extends AbstractSignParser {

	public WantedParser(SignType signType) {
		super(signType);
	}

	@Override
	public ParsedSign parse(String[] lines, Location location) throws SignValidationException {
		String action = parseContent(lines[1]);
		int    stars  = 0;
		double price  = 0D;

		String starsLine = lines[2];
		if (!starsLine.isEmpty()) {
			stars = parseAmount(starsLine);
		}

		String priceLine = lines[3];
		if (!priceLine.isEmpty()) {
			price = parsePrice(priceLine, SettingAddon.getMoneySymbol());
		}

		return new WantedParsedSign(signType, action, stars, price, location, lines);
	}

}
