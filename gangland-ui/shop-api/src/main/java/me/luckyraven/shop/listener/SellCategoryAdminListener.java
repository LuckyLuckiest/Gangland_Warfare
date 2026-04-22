package me.luckyraven.shop.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.shop.view.SellCategoryItemsAdminView;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Singleton listener for the sell-category admin editor. Delegates every click to
 * {@link SellCategoryItemsAdminView#handleClick(InventoryClickEvent)} so cursor-drops, shift-clicks, and per-slot
 * left/right clicks are all routed to the panel. Close handling moved onto
 * {@link me.luckyraven.inventory.flow.MultiPanelInventory#onEnd} when the panel became flow-aware.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class SellCategoryAdminListener implements Listener {

	private final SellCategoryItemsAdminView categoryView;

	@EventHandler(priority = EventPriority.HIGH)
	public void onClick(InventoryClickEvent event) {
		categoryView.handleClick(event);
	}

}
