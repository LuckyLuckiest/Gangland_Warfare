package me.luckyraven.shop.transaction;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.ShopItemEntry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Partial-payment purchase: caller supplies a pre-computed {@code tradeInCredit} (total value of the items the view
 * holds on the player's behalf) which offsets {@code buyPrice}. Any remainder is withdrawn in cash. On SUCCESS the
 * purchased item is delivered into the player's inventory; on any failure path the view refunds both the items and any
 * money that was not yet withdrawn.
 */
@RequiredArgsConstructor
public final class ShopTradeInService {

	private final ItemRefresherRegistry refresherRegistry;

	public TradeInResult tradeIn(Player player, PaymentHandler payment, ShopItemEntry entry,
	                             double buyPrice, double tradeInCredit) {
		double owed = Math.max(0.0, buyPrice - Math.max(0.0, tradeInCredit));

		if (payment.getBalance() < owed) {
			return TradeInResult.of(TradeInOutcome.INSUFFICIENT_FUNDS, owed, tradeInCredit);
		}

		if (owed > 0.0) {
			try {
				payment.withdraw(owed);
			} catch (PaymentException e) {
				return TradeInResult.economyError(e.getMessage());
			}
		}

		ItemStack delivery = refresherRegistry.refresh(entry.getItem(), player);
		player.getInventory().addItem(delivery).values()
		      .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

		return TradeInResult.success(owed, tradeInCredit, delivery);
	}

}
