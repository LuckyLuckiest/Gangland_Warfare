package me.luckyraven.sign.model;

import me.luckyraven.sign.SignType;
import org.bukkit.Location;

/**
 * Represents a fully parsed and validated sign with structured data. Immutable data object containing all sign
 * information.
 */
public interface ParsedSign {

	SignType getSignType();

	String getContent();

	double getPrice();

	int getAmount();

	Location getLocation();

	String[] getRawLines();

	/**
	 * Optional metadata that sign types can use for custom data
	 */
	<T> T getMetadata(String key, Class<T> type);

	boolean hasMetadata(String key);

}
