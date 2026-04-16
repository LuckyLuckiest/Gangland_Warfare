package me.luckyraven.copsncrooks.listener.trader;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.view.BarterView;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Singleton click / drag / close listener for the barter view. Delegates to
 * {@link BarterView} which owns the per-player session map.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class BarterSessionListener implements Listener {

	private final BarterView barterView;

	@EventHandler(priority = EventPriority.HIGH)
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player viewer)) return;

		BarterView.ClickOutcome outcome = barterView.handleClick(viewer, event.getInventory(),
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
		if (barterView.handleDrag(viewer, event.getInventory(), event.getRawSlots())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player viewer)) return;
		barterView.handleClose(viewer, event.getInventory());
	}

}
