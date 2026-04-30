package org.luckyraven.gangland.sign.parser;

import org.bukkit.Location;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.model.BountyParsedSign;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.validation.SignValidationException;

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
