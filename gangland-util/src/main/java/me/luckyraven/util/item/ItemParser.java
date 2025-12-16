package me.luckyraven.util.item;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class ItemParser {

	private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("\\{([^}]+)}");
	private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("(\\w+)=([^,}]+)");

	private final ItemConverterRegistry registry;

	public ItemStack parse(String itemString) {
		if (itemString == null || itemString.isBlank()) return null;

		Map<String, String> attributes = new HashMap<>();
		Matcher             matcher    = ATTRIBUTE_PATTERN.matcher(itemString);

		if (matcher.find()) {
			String  attributeString = matcher.group(1);
			Matcher keyValueMatcher = KEY_VALUE_PATTERN.matcher(attributeString);

			while (keyValueMatcher.find()) {
				String key   = keyValueMatcher.group(1);
				String value = keyValueMatcher.group(2);

				attributes.put(key, value);
			}

			// remove attributes from the string
			itemString = matcher.replaceAll("").trim();
		}

		String[] parts    = itemString.split(":", 2);
		String   type     = parts[0].toUpperCase();
		String   modifier = parts.length > 1 ? parts[1] : null;

		ItemConverter converter = getConverter(type, modifier);

		if (converter == null) {
			return null;
		}

		return converter.convert(type, modifier, attributes);
	}

	private ItemConverter getConverter(String type, String modifier) {
		// Special case: weapon
		if (type.equalsIgnoreCase("weapon")) {
			return registry.getConverter("weapon");
		}

		// Special case: ammunition
		if (type.equalsIgnoreCase("ammunition") || type.equalsIgnoreCase("ammo")) {
			return registry.getConverter("ammunition");
		}

		// Check if it's a registered unique item
		ItemConverter uniqueConverter = registry.getConverter(type.toLowerCase());
		if (uniqueConverter != null) {
			return uniqueConverter;
		}

		// Try to parse as a material
		try {
			Material.valueOf(type);

			return registry.getConverter("material");
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

}
