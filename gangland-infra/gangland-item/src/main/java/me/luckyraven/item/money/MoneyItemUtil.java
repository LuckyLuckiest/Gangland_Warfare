package me.luckyraven.item.money;

import me.luckyraven.core.ItemBuilder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * NBT helpers for cash items. Single source of truth for the marker tag and the amount/variation reads — both the
 * pickup listener and the right-click listener call through here.
 */
public final class MoneyItemUtil {

	/**
	 * Marker NBT key — presence of this byte tag identifies an item as a money/cash drop.
	 */
	public static final String MARKER_TAG = "gangland_money";

	/**
	 * Variation id (e.g. {@code small}, {@code medium}, {@code large}).
	 */
	public static final String VARIATION_TAG = "gangland_money_variation";

	/**
	 * Rolled amount for this specific drop.
	 */
	public static final String AMOUNT_TAG = "gangland_money_amount";

	private MoneyItemUtil() {
	}

	public static boolean isMoneyItem(@Nullable ItemStack item) {
		if (item == null || item.getType().isAir()) return false;
		return new ItemBuilder(item).hasNBTTag(MARKER_TAG);
	}

	public static int readAmount(ItemStack item) {
		if (!isMoneyItem(item)) return 0;
		return new ItemBuilder(item).getIntegerTagData(AMOUNT_TAG);
	}

	@Nullable
	public static String readVariationId(ItemStack item) {
		if (!isMoneyItem(item)) return null;
		String value = new ItemBuilder(item).getStringTagData(VARIATION_TAG);
		return (value == null || value.isEmpty()) ? null : value;
	}

}
