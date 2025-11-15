package me.luckyraven.bukkit.sign.parser.impl;

import me.luckyraven.bukkit.sign.SignType;
import me.luckyraven.bukkit.sign.model.ParsedSign;
import me.luckyraven.bukkit.sign.model.impl.WeaponParsedSign;
import me.luckyraven.bukkit.sign.parser.AbstractSignParser;
import me.luckyraven.bukkit.sign.validation.SignValidationException;
import org.bukkit.Location;

public class BuySignParser extends AbstractSignParser {

	public BuySignParser(SignType signType) {
		super(signType);
	}

	@Override
	public ParsedSign parse(String[] lines, Location location) throws SignValidationException {
		String content = parseContent(lines[1]);
		double price   = parsePrice(lines[2]);
		int    amount  = parseAmount(lines[3]);


		return new WeaponParsedSign(signType, content, price, amount, location, lines);
	}

}
