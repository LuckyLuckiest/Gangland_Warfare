package me.luckyraven.shop.valuation;

import me.luckyraven.shop.BarterCategory;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.inventory.ItemStack;

/**
 * Mirrors {@link CategorySellValuator} but reads {@link ShopDefinition#getBarterCategories()} and applies the trait's
 * {@code barterPriceRatio} instead of {@code sellPriceRatio}. The per-item NBT price tag is shared with the sell side
 * (see {@link CategorySellValuator#SELL_PRICE_NBT_KEY}) so a single admin-set value applies in both flows.
 */
public final class CategoryBarterValuator {

	public ItemValuation value(ShopDefinition definition, ItemStack stack, double barterPriceRatio,
	                           double moodMultiplier) {
		if (definition == null || stack == null) {
			return ItemValuation.UNKNOWN;
		}

		for (BarterCategory category : definition.getBarterCategories()) {
			ItemStack template = category.matchingTemplate(stack);
			if (template == null) {
				continue;
			}

			double bundlePrice    = readTemplatePrice(template, category.getBasePrice());
			int    templateAmount = Math.max(1, template.getAmount());
			double perItem        = bundlePrice / templateAmount;
			double unit           = perItem * barterPriceRatio * moodMultiplier;
			if (unit < 0.0) {
				unit = 0.0;
			}
			return new ItemValuation(unit, ItemValuation.Source.CATEGORY, category.getId());
		}

		return ItemValuation.UNKNOWN;
	}

	private double readTemplatePrice(ItemStack template, double fallback) {
		ItemBuilder builder = new ItemBuilder(template);
		if (!builder.hasNBTTag(CategorySellValuator.SELL_PRICE_NBT_KEY)) {
			return fallback;
		}
		Object raw = builder.getTagData(CategorySellValuator.SELL_PRICE_NBT_KEY);
		if (raw instanceof Number n) {
			double value = n.doubleValue();
			return value >= 0.0 ? value : fallback;
		}
		return fallback;
	}

}
