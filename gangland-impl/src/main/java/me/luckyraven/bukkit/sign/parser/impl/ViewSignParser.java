package me.luckyraven.bukkit.sign.parser.impl;

import me.luckyraven.bukkit.sign.SignType;
import me.luckyraven.bukkit.sign.model.ParsedSign;
import me.luckyraven.bukkit.sign.model.impl.ViewParsedSign;
import me.luckyraven.bukkit.sign.parser.AbstractSignParser;
import me.luckyraven.bukkit.sign.validation.SignValidationException;
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
