package me.luckyraven.shop.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.shop.view.ShopAdminView;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Singleton listener for the shop admin panel. Dispatches every click to
 * {@link ShopAdminView#handleClick(InventoryClickEvent)} so cursor-drop + shift-click template adding can mutate the
 * session. Close handling moved onto {@link me.luckyraven.inventory.flow.MultiPanelInventory#onEnd} inside
 * {@link me.luckyraven.shop.view.ShopAdminFlow} — the {@code ShopEditedEvent} commit fires from that hook.
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
