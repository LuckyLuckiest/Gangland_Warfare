package org.luckyraven.gangland.sign.parser;

import org.bukkit.Location;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.model.ViewParsedSign;
import org.luckyraven.gangland.sign.validation.SignValidationException;

public class ViewSignParser extends AbstractSignParser {

	public ViewSignParser(SignType signType) {
		super(signType);
	}

	@Override
	public ParsedSign parse(String[] lines, Location location) throws SignValidationException {
		String content = parseContent(lines[1]);

		// Lines 3 and 4 are ignored for view signs
		// But we still parse them as 0 for consistency

		return new ViewParsedSign(signType, content, location, lines);
	}

}
