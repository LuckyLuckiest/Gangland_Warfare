package me.luckyraven.sign.parser;

import me.luckyraven.sign.SignType;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.model.ViewParsedSign;
import me.luckyraven.sign.validation.SignValidationException;
import org.bukkit.Location;

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
