package org.luckyraven.gangland.shop.valuation;

import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.shop.ShopDefinition;

public interface SellValuator {

	ItemValuation value(ShopDefinition definition, ItemStack stack, double sellPriceRatio, double moodMultiplier);

}
