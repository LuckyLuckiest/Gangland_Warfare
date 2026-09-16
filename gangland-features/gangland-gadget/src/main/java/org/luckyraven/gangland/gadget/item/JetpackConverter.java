package org.luckyraven.gangland.gadget.item;

import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.item.ItemAttributes;

import java.util.Map;

/**
 * Converts a {@code jetpack:<name>} item string into a fully-built {@link Jetpack} ItemStack. Mirrors
 * {@link CarConverter}.
 */
@RequiredArgsConstructor
public class JetpackConverter extends ItemAttributes {

	private final JetpackAddon jetpackAddon;

	@Override
	public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
		if (modifier == null || modifier.isBlank()) {
			return null;
		}

		Jetpack jetpack = jetpackAddon.getJetpack(modifier.trim());

		if (jetpack == null) {
			return null;
		}

		ItemStack itemStack = jetpack.buildItem();

		applyAttributes(itemStack, attributes);

		return itemStack;
	}
}
