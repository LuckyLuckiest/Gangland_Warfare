package org.luckyraven.gangland.item.converter;

import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.item.ItemAttributes;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;

import java.util.Map;

/**
 * Converts a {@code unique:<key>} item string into a fully-built {@link UniqueItem} ItemStack.
 */
@RequiredArgsConstructor
public class UniqueConverter extends ItemAttributes {

	private final UniqueItemAddon uniqueItemAddon;

	@Override
	public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
		if (modifier == null || modifier.isBlank()) {
			return null;
		}

		UniqueItem unique = uniqueItemAddon.getUniqueItem(modifier.trim());

		if (unique == null) {
			return null;
		}

		ItemStack itemStack = unique.buildItem();

		applyAttributes(itemStack, attributes);

		return itemStack;
	}

}
