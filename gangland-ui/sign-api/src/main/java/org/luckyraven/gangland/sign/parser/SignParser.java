package org.luckyraven.gangland.sign.parser;

import org.bukkit.Location;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.validation.SignValidationException;

/**
 * Parses validated sign lines into structured ParsedSign objects
 */
public interface SignParser {

	/**
	 * Parse sign lines into a ParsedSign object
	 *
	 * @param lines The validated sign lines
	 * @param location The sign's location
	 *
	 * @return Parsed sign data
	 */
	ParsedSign parse(String[] lines, Location location) throws SignValidationException;

}
