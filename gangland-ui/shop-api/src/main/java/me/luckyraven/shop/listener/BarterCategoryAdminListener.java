package me.luckyraven.shop.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.shop.view.BarterCategoryItemsAdminView;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Singleton listener for the barter-category admin editor. Mirror of {@link SellCategoryAdminListener}. Close handling
 * moved onto {@link me.luckyraven.inventory.flow.MultiPanelInventory#onEnd} when the panel became flow-aware.
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
