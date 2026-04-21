package me.luckyraven.inventory.multi;

import java.util.Map;

/**
 * A single row of data returned by an {@link ItemSourceProvider}. The provider yields per-entry placeholder values; the
 * inventory renderer substitutes them into the YAML {@code Information.Item_Template} and builds the final ItemStack.
 * Providers do not construct ItemStacks themselves.
 */
public record ItemSourceEntry(Map<String, String> placeholders) {

	public static ItemSourceEntry of(Map<String, String> placeholders) {
		return new ItemSourceEntry(placeholders);
	}

}
