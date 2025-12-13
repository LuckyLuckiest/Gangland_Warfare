package me.luckyraven.util.item;

import org.bukkit.inventory.ItemStack;

import java.util.Map;

public interface ItemConverter {

	/**
	 * Converts an item definition to an ItemStack.
	 *
	 * @param type The item type (e.g., "DIAMOND_SWORD", "weapon", "phone")
	 * @param modifier Optional modifier (e.g., "baton" for weapon:baton, "blue" for LEATHER_HELMET:blue)
	 * @param attributes Additional attributes from curly brackets
	 *
	 * @return The converted ItemStack, or null if conversion fails
	 */
	ItemStack convert(String type, String modifier, Map<String, String> attributes);

}
