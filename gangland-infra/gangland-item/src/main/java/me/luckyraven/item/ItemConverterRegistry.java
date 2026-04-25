package me.luckyraven.item;

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

	public void register(String[] types, ItemConverter converter) {
		for (String type : types) {
			register(type, converter);
		}
	}

	/**
	 * Register under a canonical {@link ItemKind}. Preferred over the raw-string overload — keeps every converter
	 * registration coupled to the enum so renaming a label auto-propagates.
	 */
	public void register(ItemKind kind, ItemConverter converter) {
		register(kind.label(), converter);
	}

	public void register(ItemKind[] kinds, ItemConverter converter) {
		for (ItemKind kind : kinds) {
			register(kind, converter);
		}
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
