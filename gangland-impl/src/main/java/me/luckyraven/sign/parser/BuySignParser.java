package me.luckyraven.sign.parser;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.model.WeaponParsedSign;
import me.luckyraven.sign.validation.SignValidationException;
import org.bukkit.Location;

public class BuySignParser extends AbstractSignParser {

	public BuySignParser(SignType signType) {
		super(signType);
	}

	@Override
	public ParsedSign parse(String[] lines, Location location) throws SignValidationException {
		String content = parseContent(lines[1]);
		double price   = parsePrice(lines[2], SettingAddon.getMoneySymbol());
		int    amount  = parseAmount(lines[3]);


		return new WeaponParsedSign(signType, content, price, amount, location, lines);
	}

}
