package org.luckyraven.gangland.sign.parser;

import org.bukkit.Location;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.model.WantedParsedSign;
import org.luckyraven.gangland.sign.validation.SignValidationException;

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
			price = parsePrice(priceLine, Settings.getMoneySymbol());
		}

		return new WantedParsedSign(signType, action, stars, price, location, lines);
	}

}
