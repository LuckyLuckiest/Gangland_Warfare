package org.luckyraven.gangland.shop.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.shop.view.BarterCategoryItemsAdminView;

/**
 * Singleton listener for the barter-category admin editor. Mirror of {@link SellCategoryAdminListener}. Close handling
 * moved onto {@link MultiPanelInventory#onEnd} when the panel became flow-aware.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class BarterCategoryAdminListener implements Listener {

	private final BarterCategoryItemsAdminView categoryView;

	@EventHandler(priority = EventPriority.HIGH)
	public void onClick(InventoryClickEvent event) {
		categoryView.handleClick(event);
	}

}
