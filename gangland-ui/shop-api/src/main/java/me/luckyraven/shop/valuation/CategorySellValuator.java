package me.luckyraven.shop.valuation;

import me.luckyraven.shop.SellCategory;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.inventory.ItemStack;

public final class CategorySellValuator implements SellValuator {

	public static final String SELL_PRICE_NBT_KEY = "sell_price";

	@Override
	public ItemValuation value(ShopDefinition definition, ItemStack stack, double sellPriceRatio,
	                           double moodMultiplier) {
		if (definition == null || stack == null) {
			return ItemValuation.UNKNOWN;
		}

		for (SellCategory category : definition.getSellCategories()) {
			ItemStack template = category.matchingTemplate(stack);
			if (template == null) {
				continue;
			}

			// The template's price (NBT sell_price override, or category base price fallback) is a BUNDLE price —
			// it's what the admin charges for the template's own stack size. Per-item value = bundlePrice / templateAmount,
			// so a template of 32 slugs @ $150 values one slug at $150/32. Stack×amount scaling happens at the caller.
			double bundlePrice    = readTemplatePrice(template, category.getBasePrice());
			int    templateAmount = Math.max(1, template.getAmount());
			double perItem        = bundlePrice / templateAmount;
			double unit           = perItem * sellPriceRatio * moodMultiplier;
			if (unit < 0.0) {
				unit = 0.0;
			}
			return new ItemValuation(unit, ItemValuation.Source.CATEGORY, category.getId());
		}

		return ItemValuation.UNKNOWN;
	}

	private double readTemplatePrice(ItemStack template, double fallback) {
		ItemBuilder builder = new ItemBuilder(template);
		if (!builder.hasNBTTag(SELL_PRICE_NBT_KEY)) {
			return fallback;
		}
		Object raw = builder.getTagData(SELL_PRICE_NBT_KEY);
		if (raw instanceof Number n) {
			double value = n.doubleValue();
			return value >= 0.0 ? value : fallback;
		}
		return fallback;
	}

}
