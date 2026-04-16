package me.luckyraven.shop.transaction;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.shop.ShopItemEntry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable barter mutation for any shop integration. Barter is a pure item-for-item swap: the player offers a list of
 * items whose combined barter-value (computed by the caller) must meet or exceed the entry's asking value. On success
 * a refreshed copy of {@link ShopItemEntry#getItem()} is delivered. <strong>No economy money is involved at any
 * point</strong>.
 *
 * <p>The {@code offered} list is the source of truth for what the player is giving up. Callers (e.g. drop-zone GUIs)
 * are expected to have already moved the offered items out of the player's inventory before invoking this method —
 * the service does <em>not</em> double-check the player inventory and does <em>not</em> remove anything from it. It
 * simply hands over the entry item.</p>
 */
@RequiredArgsConstructor
public final class ShopBarterService {

	private final ItemRefresherRegistry refresherRegistry;

	public BarterResult barter(Player player, ShopItemEntry entry, double askingValue, double offeredValue,
	                           List<ItemStack> offered) {
		if (offered == null || offered.isEmpty()) {
			return BarterResult.of(BarterOutcome.NOT_BARTERABLE, askingValue, offeredValue);
		}
		if (offeredValue < askingValue) {
			return BarterResult.of(BarterOutcome.INSUFFICIENT_VALUE, askingValue, offeredValue);
		}

		List<ItemStack> consumed = new ArrayList<>(offered.size());
		for (ItemStack stack : offered) {
			if (stack == null) continue;
			consumed.add(stack.clone());
		}

		PlayerInventory inv      = player.getInventory();
		ItemStack       delivery = refresherRegistry.refresh(entry.getItem(), player);
		inv.addItem(delivery).values()
		   .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

		return BarterResult.success(delivery, consumed, askingValue, offeredValue);
	}

}
