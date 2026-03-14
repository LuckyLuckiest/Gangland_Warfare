package me.luckyraven.item.converter;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemAttributes;
import me.luckyraven.util.item.wearable.Wearable;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Converts a {@code wearable:<name>} item string into a fully-built {@link Wearable} ItemStack.
 *
 * <p>Usage examples:
 * <pre>
 *   wearable:police_vest
 *   wearable:heavy_vest{color=BLUE}
 * </pre>
 *
 * <p>The {@code modifier} is the wearable registry key (the name defined in {@code wearables.yml}).
 * Attributes supported by {@link me.luckyraven.item.ItemAttributes#applyAttributes} (name, lore, color) are applied
 * after the item is built, allowing per-use overrides without altering the registered wearable definition.
 */
@RequiredArgsConstructor
public class WearableConverter extends ItemAttributes {

	private final WearableService wearableService;

	@Override
	public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
		if (modifier == null || modifier.isBlank()) return null;

		Wearable wearable = wearableService.getWearable(modifier.trim());

		if (wearable == null) return null;

		ItemStack itemStack = wearable.buildItem();

		applyAttributes(itemStack, attributes);

		return itemStack;
	}

}
