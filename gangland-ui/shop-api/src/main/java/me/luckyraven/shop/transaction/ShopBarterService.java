package me.luckyraven.shop.transaction;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.ShopItemEntry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Reusable barter mutation for any shop integration. Validates the player holds enough of the configured
 * {@code tradeFor} item, removes that stack, and delivers a refreshed copy of {@link ShopItemEntry#getItem()}. The
 * caller maps the {@link BarterResult} outcome to whatever user-facing message is appropriate.
 */
@RequiredArgsConstructor
public final class ShopBarterService {

	private final ItemRefresherRegistry refresherRegistry;

	public BarterResult barter(Player player, ShopItemEntry entry) {
		ItemStack cost = entry.getTradeFor();
		if (cost == null) {
			return BarterResult.of(BarterOutcome.NOT_BARTERABLE);
		}

		PlayerInventory inv = player.getInventory();
		if (!inv.containsAtLeast(cost, cost.getAmount())) {
			return BarterResult.of(BarterOutcome.MISSING_ITEMS);
		}

		ItemStack consumed = cost.clone();
		inv.removeItem(consumed);

		ItemStack delivery = refresherRegistry.refresh(entry.getItem(), player);
		inv.addItem(delivery).values()
		   .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

		return BarterResult.success(delivery, consumed);
	}

}
