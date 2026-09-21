package org.luckyraven.gangland.shop.admin.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.gangland.shop.admin.view.ShopAdminFlow;
import org.luckyraven.gangland.shop.admin.view.ShopAdminView;

/**
 * Singleton listener for the shop admin panel. Dispatches every click to
 * {@link ShopAdminView#handleClick(InventoryClickEvent)} so cursor-drop + shift-click template adding can mutate the
 * session. WS4 G1b: close handling is {@link MenuFlow}'s flow-wide {@code onEnd}, wired once inside
 * {@link ShopAdminFlow} (the old per-render {@code MultiPanelInventory#onEnd} registration has no equivalent on
 * the new immutable-at-build flow) — the {@code ShopEditedEvent} commit fires from that hook.
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
