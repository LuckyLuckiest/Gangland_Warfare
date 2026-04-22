package me.luckyraven.copsncrooks.listener.trader;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.view.BarterView;
import me.luckyraven.core.bean.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Singleton click / drag listener for the barter panel. Close handling moved onto
 * {@link me.luckyraven.inventory.flow.MultiPanelInventory#onEnd} inside {@link BarterView#render} when the panel
 * became flow-aware, so no dedicated close event handler is needed here.
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

}
