package org.luckyraven.gangland.sign.parser;

import org.bukkit.Location;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.model.WeaponParsedSign;
import org.luckyraven.gangland.sign.validation.SignValidationException;

public class TradeSignParser extends AbstractSignParser {

	public TradeSignParser(SignType signType) {
		super(signType);
	}

	@Override
	public ParsedSign parse(String[] lines, Location location) throws SignValidationException {
		String content = parseContent(lines[1]);
		double price   = parsePrice(lines[2], Settings.getMoneySymbol());
		int    amount  = parseAmount(lines[3]);

		return new WeaponParsedSign(signType, content, price, amount, location, lines);
	}

}
