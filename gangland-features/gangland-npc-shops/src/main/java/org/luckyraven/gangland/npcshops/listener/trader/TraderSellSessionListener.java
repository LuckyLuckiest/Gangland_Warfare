package org.luckyraven.gangland.copsncrooks.listener.trader;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.luckyraven.gangland.copsncrooks.npc.trader.view.SellView;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;

/**
 * Singleton click / drag listener for the sell panel. Delegates to {@link SellView#handleClick} /
 * {@link SellView#handleDrag} which look up the per-player dropzone state and either route the event into the dropzone
 * or cancel it. Close handling moved onto {@link MultiPanelInventory#onEnd} inside {@link SellView#render} when the
 * panel became flow-aware, so no dedicated close event handler is needed here anymore.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TraderSellSessionListener implements Listener {

	private final SellView sellView;

	@EventHandler(priority = EventPriority.HIGH)
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player viewer)) return;

		SellView.ClickOutcome outcome = sellView.handleClick(viewer, event.getInventory(),
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

}
