package me.luckyraven.market;

import me.luckyraven.item.ItemSerializerRegistry;
import me.luckyraven.market.contract.MarketPriceContract;
import me.luckyraven.shop.SellCategory;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.valuation.CategorySellValuator;
import me.luckyraven.shop.valuation.ItemValuation;
import me.luckyraven.shop.valuation.SellValuator;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.inventory.ItemStack;

/**
 * Drop-in replacement for {@link CategorySellValuator} that routes every sell valuation through the live market price
 * when available. Items not yet tracked are auto-seeded into {@link MarketPriceContract} with the
 * {@link SellCategory#getBasePrice() category base price}. NBT-overridden items skip the market entirely — admin
 * one-offs are not allowed to move the item's market price.
 */
public final class MarketAwareSellValuator implements SellValuator {

	private final MarketPriceContract    market;
	private final ItemSerializerRegistry itemSerializerRegistry;
	private final CategorySellValuator   fallback = new CategorySellValuator();

	public MarketAwareSellValuator(MarketPriceContract market, ItemSerializerRegistry itemSerializerRegistry) {
		this.market                 = market;
		this.itemSerializerRegistry = itemSerializerRegistry;
	}

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

			// Per-item NBT override wins — these are admin-authored unique variants that should not participate
			// in the market's supply/demand tracking.
			ItemBuilder builder = new ItemBuilder(template);
			if (builder.hasNBTTag(CategorySellValuator.SELL_PRICE_NBT_KEY)) {
				return fallback.value(definition, stack, sellPriceRatio, moodMultiplier);
			}

			int    templateAmount = Math.max(1, template.getAmount());
			double basePerItem    = category.getBasePrice() / templateAmount;
			String itemId         = itemSerializerRegistry.serialize(template);
			if (itemId == null) {
				return fallback.value(definition, stack, sellPriceRatio, moodMultiplier);
			}

			// Seed on first encounter then read back the effective price.
			market.ensureRegistered(itemId, basePerItem);
			double marketPerItem = market.currentPrice(itemId);
			if (marketPerItem <= 0D) {
				marketPerItem = basePerItem;
			}

			double unit = marketPerItem * sellPriceRatio * moodMultiplier;
			if (unit < 0D) {
				unit = 0D;
			}
			return new ItemValuation(unit, ItemValuation.Source.CATEGORY, category.getId());
		}

		return ItemValuation.UNKNOWN;
	}
}
