package me.luckyraven.shop.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.shop.view.SellCategoryItemsAdminView;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Singleton listener for the sell-category admin editor. Handles right-clicks (opens the per-item price editor) and
 * inventory-close (commits the template snapshot). Delegates back to {@link SellCategoryItemsAdminView} which owns the
 * per-admin session map.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class SellCategoryAdminListener implements Listener {

	private final SellCategoryItemsAdminView categoryView;

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player admin)) return;
		if (event.getClick() != ClickType.RIGHT) return;

		boolean cancel = categoryView.handleRightClick(admin, event.getInventory(), event.getRawSlot(),
		                                               event.getCurrentItem(), event.getCursor());
		if (cancel) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player admin)) return;
		categoryView.handleClose(admin, event.getInventory());
	}

}
