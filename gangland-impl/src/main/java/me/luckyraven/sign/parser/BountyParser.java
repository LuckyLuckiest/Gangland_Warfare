package me.luckyraven.sign.parser;

import me.luckyraven.sign.SignType;
import me.luckyraven.sign.model.BountyParsedSign;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.validation.SignValidationException;
import org.bukkit.Location;

public class BountyParser extends AbstractSignParser {

	public BountyParser(SignType signType) {
		super(signType);
	}

	@Override
	public ParsedSign parse(String[] lines, Location location) throws SignValidationException {
		String content = parseContent(lines[1]);
		double price   = 0D;
		int    amount  = 0;

		return new BountyParsedSign(signType, content, price, amount, location, lines);
	}

}
