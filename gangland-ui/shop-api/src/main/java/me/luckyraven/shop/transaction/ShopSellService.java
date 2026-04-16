package me.luckyraven.shop.transaction;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Mirror of {@link ShopPurchaseService} for the sell direction. The caller view owns the offered {@link ItemStack}
 * contents (they live in the view's inventory, not the player's) — the service only performs the money side of the
 * transaction and reports back. The view is responsible for consuming or refunding items based on the outcome.
 */
@RequiredArgsConstructor
public final class ShopSellService {

	public SellResult sell(Player player, PaymentHandler payment, List<ItemStack> offeredItems, double offeredTotal) {
		if (offeredItems == null || offeredItems.isEmpty() || offeredTotal <= 0.0) {
			return SellResult.of(SellOutcome.NOTHING_VALUED);
		}

		int itemCount = 0;
		for (ItemStack stack : offeredItems) {
			if (stack != null) {
				itemCount += stack.getAmount();
			}
		}

		if (itemCount == 0) {
			return SellResult.of(SellOutcome.NOTHING_VALUED);
		}

		try {
			payment.deposit(offeredTotal);
		} catch (PaymentException e) {
			return SellResult.economyError(e.getMessage());
		}

		return SellResult.success(offeredTotal, itemCount);
	}

}
