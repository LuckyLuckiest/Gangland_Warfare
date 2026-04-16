package me.luckyraven.shop.valuation;

import me.luckyraven.shop.ShopDefinition;
import org.bukkit.inventory.ItemStack;

public interface SellValuator {

	ItemValuation value(ShopDefinition definition, ItemStack stack, double sellPriceRatio, double moodMultiplier);

}
