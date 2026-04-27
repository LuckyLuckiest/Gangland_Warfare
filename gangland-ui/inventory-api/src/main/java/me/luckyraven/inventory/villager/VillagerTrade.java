package me.luckyraven.inventory.villager;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * One villager trade: a result item, one or two ingredients, a usage cap, and an optional callback fired when the
 * player completes the trade. {@link #toRecipe()} produces a fresh {@link MerchantRecipe} per call so the
 * {@link VillagerInventory} can keep an identity-based mapping back to this record.
 */
public record VillagerTrade(@NotNull ItemStack result,
                            @NotNull ItemStack firstIngredient,
                            @Nullable ItemStack secondIngredient,
                            int maxUses,
                            @Nullable Consumer<Player> onPurchase) {

	public static VillagerTrade of(@NotNull ItemStack result,
	                               @NotNull ItemStack ingredient,
	                               int maxUses,
	                               @Nullable Consumer<Player> onPurchase) {
		return new VillagerTrade(result, ingredient, null, maxUses, onPurchase);
	}

	public static VillagerTrade of(@NotNull ItemStack result,
	                               @NotNull ItemStack firstIngredient,
	                               @NotNull ItemStack secondIngredient,
	                               int maxUses,
	                               @Nullable Consumer<Player> onPurchase) {
		return new VillagerTrade(result, firstIngredient, secondIngredient, maxUses, onPurchase);
	}

	@NotNull MerchantRecipe toRecipe() {
		MerchantRecipe recipe = new MerchantRecipe(result, maxUses);
		recipe.addIngredient(firstIngredient);

		if (secondIngredient != null) {
			recipe.addIngredient(secondIngredient);
		}

		return recipe;
	}
}
