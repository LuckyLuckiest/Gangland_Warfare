package me.luckyraven.util.item;

import java.util.HashMap;
import java.util.Map;

public class ItemConverterRegistry {

	private final Map<String, ItemConverter> converters = new HashMap<>();

	/**
	 * Registers a converter for a specific type.
	 *
	 * @param type The type identifier (e.g., "weapon", "ammunition", "phone")
	 * @param converter The converter implementation
	 */
	public void register(String type, ItemConverter converter) {
		converters.put(type.toLowerCase(), converter);
	}

	/**
	 * Gets a converter for a specific type.
	 *
	 * @param type The type identifier
	 *
	 * @return The converter, or null if not found
	 */
	public ItemConverter getConverter(String type) {
		return converters.get(type.toLowerCase());
	}

	/**
	 * Checks if a converter is registered for a type.
	 *
	 * @param type The type identifier
	 *
	 * @return True if registered, false otherwise
	 */
	public boolean hasConverter(String type) {
		return converters.containsKey(type.toLowerCase());
	}

}
