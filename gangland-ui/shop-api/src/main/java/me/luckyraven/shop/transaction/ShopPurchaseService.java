package me.luckyraven.shop.transaction;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.ShopItemEntry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Reusable purchase mutation for any shop integration. Given a {@link PaymentHandler} adapter, the service checks the
 * balance, debits the account, produces a fresh copy of the sold item via {@link ItemRefresherRegistry}, and delivers
 * it into the player's inventory (dropping any leftovers at the player's feet if the inventory is full). No user-facing
 * messaging happens here — the returned {@link PurchaseResult} lets the caller decide what to say.
 */
@RequiredArgsConstructor
public final class ShopPurchaseService {

	private final ItemRefresherRegistry refresherRegistry;

	public PurchaseResult purchase(Player player, PaymentHandler payment, ShopItemEntry entry, double finalPrice) {
		if (payment.getBalance() < finalPrice) {
			return PurchaseResult.of(PurchaseOutcome.INSUFFICIENT_FUNDS);
		}

		try {
			payment.withdraw(finalPrice);
		} catch (PaymentException e) {
			return PurchaseResult.economyError(e.getMessage());
		}

		ItemStack delivery = refresherRegistry.refresh(entry.getItem(), player);
		player.getInventory().addItem(delivery).values()
		      .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

		return PurchaseResult.success(delivery, finalPrice);
	}

}
