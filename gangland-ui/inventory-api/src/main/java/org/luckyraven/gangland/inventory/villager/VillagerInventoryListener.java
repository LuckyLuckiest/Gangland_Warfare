package org.luckyraven.gangland.inventory.villager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Observes Bukkit merchant-inventory events on behalf of every open {@link VillagerInventory} wrapper. Fires
 * {@link VillagerTrade#onPurchase()} when the player commits a trade by interacting with the result slot, and keeps the
 * {@link VillagerInventoryRegistry} clean on close / quit.
 *
 * <p>Spigot-only: no Paper {@code TradeSelectEvent}. Purchase detection runs through {@link InventoryClickEvent} on
 * slot 2 (the result slot) of a {@link MerchantInventory} with a "consume" action.
 */
@ListenerHandler
public final class VillagerInventoryListener implements Listener {

	private static final int RESULT_SLOT = 2;

	/**
	 * Click actions that result in the player taking the trade output. Vanilla performs the trade after this handler
	 * returns (we run at MONITOR with ignoreCancelled), so observing these actions is equivalent to "trade committed".
	 */
	private static final Set<InventoryAction> CONSUME_ACTIONS = EnumSet.of(
			InventoryAction.PICKUP_ALL,
			InventoryAction.PICKUP_HALF,
			InventoryAction.PICKUP_ONE,
			InventoryAction.PICKUP_SOME,
			InventoryAction.MOVE_TO_OTHER_INVENTORY,
			InventoryAction.HOTBAR_SWAP,
			InventoryAction.HOTBAR_MOVE_AND_READD,
			InventoryAction.COLLECT_TO_CURSOR,
			InventoryAction.DROP_ALL_SLOT,
			InventoryAction.DROP_ONE_SLOT
	);

	private final VillagerInventoryRegistry registry;

	public VillagerInventoryListener(VillagerInventoryRegistry registry) {
		this.registry = registry;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;

		Inventory top = event.getView().getTopInventory();
		if (top.getType() != InventoryType.MERCHANT) return;
		if (event.getRawSlot() != RESULT_SLOT) return;
		if (event.getClickedInventory() != top) return;
		if (!CONSUME_ACTIONS.contains(event.getAction())) return;

		VillagerInventory wrapper = registry.find(player.getUniqueId());
		if (wrapper == null) return;

		MerchantRecipe selected = ((MerchantInventory) top).getSelectedRecipe();
		if (selected == null) return;

		VillagerTrade trade = wrapper.findTradeFor(selected);
		if (trade == null) return;

		Consumer<Player> onPurchase = trade.onPurchase();
		if (onPurchase != null) {
			onPurchase.accept(player);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onClose(InventoryCloseEvent event) {
		if (event.getInventory().getType() != InventoryType.MERCHANT) return;
		if (!(event.getPlayer() instanceof Player player)) return;

		registry.unregister(player.getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		registry.unregister(event.getPlayer().getUniqueId());
	}
}
