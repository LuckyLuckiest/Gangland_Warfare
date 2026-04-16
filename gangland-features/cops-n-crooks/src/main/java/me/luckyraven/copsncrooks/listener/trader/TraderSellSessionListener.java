package me.luckyraven.copsncrooks.listener.trader;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.view.TraderSellView;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Singleton click / drag / close listener for the player-facing sell view. Delegates to {@link TraderSellView} which
 * owns the per-player session map.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TraderSellSessionListener implements Listener {

	private final TraderSellView sellView;

	@EventHandler(priority = EventPriority.HIGH)
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player viewer)) return;

		TraderSellView.ClickOutcome outcome = sellView.handleClick(viewer, event.getInventory(),
		                                                           event.getClickedInventory(), event.getSlot(),
		                                                           event.getAction(), event.getCurrentItem());
		if (outcome.cancel()) {
			event.setCancelled(true);
		}
		if (outcome.replace()) {
			event.setCurrentItem(outcome.replacementCurrent());
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onDrag(InventoryDragEvent event) {
		if (!(event.getWhoClicked() instanceof Player viewer)) return;
		if (sellView.handleDrag(viewer, event.getInventory(), event.getRawSlots())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player viewer)) return;
		sellView.handleClose(viewer, event.getInventory());
	}

}
