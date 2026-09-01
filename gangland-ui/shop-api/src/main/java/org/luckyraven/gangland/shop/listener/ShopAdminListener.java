package org.luckyraven.gangland.shop.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.shop.view.ShopAdminFlow;
import org.luckyraven.gangland.shop.view.ShopAdminView;

/**
 * Singleton listener for the shop admin panel. Dispatches every click to
 * {@link ShopAdminView#handleClick(InventoryClickEvent)} so cursor-drop + shift-click template adding can mutate the
 * session. Close handling moved onto {@link MultiPanelInventory#onEnd} inside {@link ShopAdminFlow} — the
 * {@code ShopEditedEvent} commit fires from that hook.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class ShopAdminListener implements Listener {

	private final ShopAdminView shopAdminView;

	@EventHandler(priority = EventPriority.HIGH)
	public void onClick(InventoryClickEvent event) {
		shopAdminView.handleClick(event);
	}

}
