package me.luckyraven.sign.parser;

import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.validation.SignValidationException;
import org.bukkit.Location;

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
