package me.luckyraven.item;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class ItemParser {

	private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("\\{([^}]+)}");
	private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("(\\w+)=([^,}]+)");

	private final ItemConverterRegistry registry;

	@Nullable
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

		ItemConverter converter = getConverter(type);

		if (converter == null) return null;

		return converter.convert(type, modifier, attributes);
	}

	@Nullable
	private ItemConverter getConverter(String type) {
		if (!registry.hasConverter(type)) {
			try {
				Material.valueOf(type);
				return registry.getConverter("material");
			} catch (IllegalArgumentException ignored) { }
		}

		return registry.getConverter(type);
	}

}
