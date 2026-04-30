package org.luckyraven.gangland.shop.valuation;

import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.core.ItemBuilder;
import org.luckyraven.gangland.item.ItemSerializerRegistry;
import org.luckyraven.gangland.shop.SellCategory;
import org.luckyraven.gangland.shop.ShopDefinition;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RequiredArgsConstructor
public final class CategorySellValuator implements SellValuator {

	public static final String SELL_PRICE_NBT_KEY = "sell_price";

	private static final int          PRICE_SCALE = 2;
	private static final RoundingMode PRICE_MODE  = RoundingMode.HALF_UP;

	private final ItemSerializerRegistry serializerRegistry;

	@Override
	public ItemValuation value(ShopDefinition definition, ItemStack stack, double sellPriceRatio,
	                           double moodMultiplier) {
		if (definition == null || stack == null) {
			return ItemValuation.UNKNOWN;
		}

		for (SellCategory category : definition.getSellCategories()) {
			ItemStack template = category.matchingTemplate(stack, serializerRegistry);
			if (template == null) {
				continue;
			}

			BigDecimal bundlePrice    = readTemplatePrice(template, category.getBasePrice());
			int        templateAmount = Math.max(1, template.getAmount());
			BigDecimal perItem        = bundlePrice.divide(BigDecimal.valueOf(templateAmount), PRICE_SCALE, PRICE_MODE);
			BigDecimal unit = perItem.multiply(BigDecimal.valueOf(sellPriceRatio * moodMultiplier))
			                         .setScale(PRICE_SCALE, PRICE_MODE);
			if (unit.signum() < 0) {
				unit = BigDecimal.ZERO;
			}
			return new ItemValuation(unit, ItemValuation.Source.CATEGORY, category.getId());
		}

		return ItemValuation.UNKNOWN;
	}

	private BigDecimal readTemplatePrice(ItemStack template, BigDecimal fallback) {
		ItemBuilder builder = new ItemBuilder(template);
		if (!builder.hasNBTTag(SELL_PRICE_NBT_KEY)) {
			return fallback;
		}
		Object raw = builder.getTagData(SELL_PRICE_NBT_KEY);
		if (raw == null) {
			return fallback;
		}
		if (raw instanceof Number n) {
			BigDecimal value = BigDecimal.valueOf(n.doubleValue());
			return value.signum() >= 0 ? value : fallback;
		}
		try {
			BigDecimal value = new BigDecimal(String.valueOf(raw).trim());
			return value.signum() >= 0 ? value : fallback;
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

}
