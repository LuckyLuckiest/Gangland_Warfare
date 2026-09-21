package org.luckyraven.gangland.npcshops.listener.trader;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.luckyraven.gangland.npcshops.trader.view.BarterView;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Singleton click / drag listener for the barter panel. Runs alongside Keystone's own {@code MenuListener} (which
 * already leaves an interactive slot's click uncancelled — see {@code DropzoneSlotComponent}); this listener adds
 * the barter-specific behaviour Keystone's generic mechanism doesn't do on its own: multi-slot stacking placement
 * for a shift-click from the player's inventory, and scheduling a recompute after a drop/drag lands in the
 * dropzone. Close handling is Keystone's own item-return contract, wired via {@link BarterView#onFlowEnd} —
 * see {@link BarterView}'s class doc.
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
