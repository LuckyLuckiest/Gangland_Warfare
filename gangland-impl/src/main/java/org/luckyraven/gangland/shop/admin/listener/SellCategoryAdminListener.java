package org.luckyraven.gangland.shop.admin.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.gangland.shop.admin.view.SellCategoryItemsAdminView;

/**
 * Singleton listener for the sell-category admin editor. Delegates every click to
 * {@link SellCategoryItemsAdminView#handleClick(InventoryClickEvent)} so cursor-drops, shift-clicks, and per-slot
 * left/right clicks are all routed to the panel. WS4 G1b: close handling is {@link MenuFlow}'s flow-wide
 * {@code onEnd}, wired once inside {@code ShopAdminFlow}.
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
