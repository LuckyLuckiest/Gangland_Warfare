package org.luckyraven.gangland.sign.type.trade;

import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.sign.type.Sign;
import org.luckyraven.keystone.item.ItemParser;

public abstract class BaseTradeSign implements Sign {

	/**
	 * Resolves line 3 of a trade sign. Checks the unique-item registry first by the raw content (backward compatible
	 * with every placed sign that stores a bare unique-item key with no type prefix, exactly as the deleted
	 * {@code getUniqueOrMaterialItem} did), then falls through to the full {@link ItemParser} grammar — which covers
	 * a bare material name ({@code DIAMOND_SWORD}, via {@code ItemConverterRegistry#resolve}'s material fallback)
	 * and any prefixed definition string ({@code weapon:rifle}, {@code unique:x}, {@code car:y}, {@code money:…}).
	 */
	protected ItemStack getDefinedItem(String content, UniqueItemAddon uniqueItemAddon, ItemParser itemParser) {
		UniqueItem uniqueItem = uniqueItemAddon.getUniqueItem(content);

		if (uniqueItem != null) return uniqueItem.buildItem();

		return itemParser.parse(content);
	}
}
