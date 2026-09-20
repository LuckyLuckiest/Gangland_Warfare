package org.luckyraven.gangland.gadget.item;

import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.gadget.grapple.Grapple;
import org.luckyraven.gangland.gadget.grapple.config.GrappleAddon;
import org.luckyraven.gangland.item.ItemAttributes;

import java.util.Map;

/**
 * Converts a {@code grapple:<name>} item string into a fully-built {@link Grapple} ItemStack. Mirrors
 * {@link JetpackConverter}.
 */
@RequiredArgsConstructor
public class GrappleConverter extends ItemAttributes {

	private final GrappleAddon grappleAddon;

	@Override
	public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
		if (modifier == null || modifier.isBlank()) {
			return null;
		}

		Grapple grapple = grappleAddon.getGrapple(modifier.trim());

		if (grapple == null) {
			return null;
		}

		ItemStack itemStack = grapple.buildItem();

		applyAttributes(itemStack, attributes);

		return itemStack;
	}
}
