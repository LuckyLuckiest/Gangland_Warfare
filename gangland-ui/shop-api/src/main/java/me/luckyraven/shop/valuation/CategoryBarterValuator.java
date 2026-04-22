package me.luckyraven.shop.valuation;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.item.ItemSerializerRegistry;
import me.luckyraven.shop.BarterCategory;
import me.luckyraven.shop.ShopDefinition;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Mirrors {@link CategorySellValuator} but reads {@link ShopDefinition#getBarterCategories()} and applies the trait's
 * {@code barterPriceRatio} instead of {@code sellPriceRatio}. The per-item NBT price tag is shared with the sell side
 * (see {@link CategorySellValuator#SELL_PRICE_NBT_KEY}) so a single admin-set value applies in both flows.
 */
@RequiredArgsConstructor
public final class CategoryBarterValuator {

	private static final int          PRICE_SCALE = 2;
	private static final RoundingMode PRICE_MODE  = RoundingMode.HALF_UP;

	private final ItemSerializerRegistry serializerRegistry;

	public ItemValuation value(ShopDefinition definition, ItemStack stack, double barterPriceRatio,
	                           double moodMultiplier) {
		if (definition == null || stack == null) {
			return ItemValuation.UNKNOWN;
		}

		for (BarterCategory category : definition.getBarterCategories()) {
			ItemStack template = category.matchingTemplate(stack, serializerRegistry);
			if (template == null) {
				continue;
			}

			BigDecimal bundlePrice    = readTemplatePrice(template, category.getBasePrice());
			int        templateAmount = Math.max(1, template.getAmount());
			BigDecimal perItem        = bundlePrice.divide(BigDecimal.valueOf(templateAmount), PRICE_SCALE, PRICE_MODE);
			BigDecimal unit = perItem.multiply(BigDecimal.valueOf(barterPriceRatio * moodMultiplier))
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
		if (!builder.hasNBTTag(CategorySellValuator.SELL_PRICE_NBT_KEY)) {
			return fallback;
		}
		Object raw = builder.getTagData(CategorySellValuator.SELL_PRICE_NBT_KEY);
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
