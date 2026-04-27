package me.luckyraven.inventory.villager;

import me.luckyraven.core.utilities.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal wrapper over Bukkit's native villager-trade UI. Holds a list of {@link VillagerTrade}s, builds a fresh
 * {@link Merchant} on every {@link #open(Player)} call, and registers itself with the {@link VillagerInventoryRegistry}
 * so the global listener can fire {@link VillagerTrade#onPurchase()} when the player completes a trade.
 *
 * <p>Build trades with {@link #addTrade(VillagerTrade)} (chainable), then call {@link #open(Player)}.
 */
public final class VillagerInventory {

	private final VillagerInventoryRegistry          registry;
	private final String                             title;
	private final List<VillagerTrade>                trades;
	private final Map<MerchantRecipe, VillagerTrade> recipeToTrade;

	public VillagerInventory(@NotNull VillagerInventoryRegistry registry, @NotNull String rawTitle) {
		this.registry      = registry;
		this.title         = ChatUtil.color(rawTitle);
		this.trades        = new ArrayList<>();
		this.recipeToTrade = new IdentityHashMap<>();
	}

	public @NotNull VillagerInventory addTrade(@NotNull VillagerTrade trade) {
		trades.add(trade);
		return this;
	}

	// Bukkit.createMerchant(String) is deprecated in favour of MenuType.MERCHANT.builder().title(...).merchant(...),
	// but Paper's runtime swaps the builder's title() to take an Adventure Component (NoSuchMethodError on Paper at
	// runtime). Stick with the deprecated single-arg form — only path that keeps a custom title across Spigot+Paper.
	@SuppressWarnings("deprecation")
	public void open(@NotNull Player player) {
		Merchant merchant = Bukkit.createMerchant(title);

		List<MerchantRecipe> recipes = new ArrayList<>(trades.size());
		recipeToTrade.clear();

		for (VillagerTrade trade : trades) {
			MerchantRecipe recipe = trade.toRecipe();
			recipes.add(recipe);
			recipeToTrade.put(recipe, trade);
		}

		merchant.setRecipes(recipes);

		// openMerchant fires InventoryCloseEvent for any prior open inventory synchronously, which lets the listener
		// unregister the previous wrapper before we register this one.
		player.openMerchant(merchant, true);

		registry.register(player.getUniqueId(), this);
	}

	@Nullable VillagerTrade findTradeFor(@NotNull MerchantRecipe recipe) {
		return recipeToTrade.get(recipe);
	}
}
