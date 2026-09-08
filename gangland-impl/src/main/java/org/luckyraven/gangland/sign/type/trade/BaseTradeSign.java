package org.luckyraven.gangland.sign.type.trade;

import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.sign.type.Sign;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.item.ItemSerializerRegistry;
import org.luckyraven.keystone.item.spi.ItemDefinitions;

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

	/**
	 * Similarity check shared by {@link BuySign} and {@link SellSign}: two stacks trade as the same item if they
	 * describe to the same {@link ItemDefinitions} string <em>and</em>, when that description is the
	 * {@link ItemKind#MATERIAL material} catch-all tier (which carries no identity beyond the raw
	 * {@link org.bukkit.Material}), they also pass {@link ItemStack#isSimilar} — otherwise a
	 * {@code [SELL] DIAMOND_SWORD} sign would take any enchanted, renamed or damaged sword along with a plain one.
	 * Falls back to {@code isSimilar} entirely when either stack does not describe (outside the serializer
	 * vocabulary).
	 */
	protected static boolean sameTradeDefinition(ItemSerializerRegistry serializers, ItemStack a, ItemStack b) {
		String descriptionA = ItemDefinitions.describe(serializers, a);
		String descriptionB = ItemDefinitions.describe(serializers, b);

		if (descriptionA == null || descriptionB == null) {
			return a != null && b != null && a.isSimilar(b);
		}

		if (!descriptionA.equals(descriptionB)) {
			return false;
		}

		if (descriptionA.startsWith(ItemKind.MATERIAL.label() + ":")) {
			return a.isSimilar(b);
		}

		return true;
	}
}
