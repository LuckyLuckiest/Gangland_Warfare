package org.luckyraven.gangland.shop.transaction;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.item.ItemRefresherRegistry;
import org.luckyraven.gangland.shop.ShopItemEntry;

import java.math.BigDecimal;

/**
 * Reusable purchase mutation for any shop integration. Given a {@link PaymentHandler} adapter, the service checks the
 * balance, debits the account, produces a fresh copy of the sold item via {@link ItemRefresherRegistry}, and delivers
 * it into the player's inventory (dropping any leftovers at the player's feet if the inventory is full). No user-facing
 * messaging happens here — the returned {@link PurchaseResult} lets the caller decide what to say.
 */
@RequiredArgsConstructor
public final class ShopPurchaseService {

	private final ItemRefresherRegistry refresherRegistry;

	public PurchaseResult purchase(Player player, PaymentHandler payment, ShopItemEntry entry, BigDecimal finalPrice) {
		return purchase(player, payment, entry, finalPrice, 1);
	}

	/**
	 * Purchase {@code copies} of the {@link ShopItemEntry}'s templated item. Each copy is refreshed independently so
	 * stateful items (unique UUIDs, ammo loads, etc.) stay correct. The template's own stack amount is preserved per
	 * copy — so a 32-item template with {@code copies=5} delivers five stacks of 32 (160 items total) and debits
	 * {@code finalTotal} once.
	 */
	public PurchaseResult purchase(Player player, PaymentHandler payment, ShopItemEntry entry, BigDecimal finalTotal,
	                               int copies) {
		if (copies < 1) {
			copies = 1;
		}
		if (payment.getBalance().compareTo(finalTotal) < 0) {
			return PurchaseResult.of(PurchaseOutcome.INSUFFICIENT_FUNDS);
		}

		try {
			payment.withdraw(finalTotal);
		} catch (PaymentException e) {
			return PurchaseResult.economyError(e.getMessage());
		}

		ItemStack firstDelivery = null;
		for (int i = 0; i < copies; i++) {
			ItemStack delivery = refresherRegistry.refresh(entry.getItem(), player);
			if (firstDelivery == null) {
				firstDelivery = delivery;
			}
			player.getInventory().addItem(delivery).values()
			      .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
		}

		return PurchaseResult.success(firstDelivery, finalTotal);
	}

}
