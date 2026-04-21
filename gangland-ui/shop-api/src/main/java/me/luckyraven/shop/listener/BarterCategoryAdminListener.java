package me.luckyraven.shop.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.shop.view.BarterCategoryItemsAdminView;
import me.luckyraven.core.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Singleton listener for the barter-category admin editor. Mirror of {@link SellCategoryAdminListener}; delegates back
 * to {@link BarterCategoryItemsAdminView} which owns the per-admin session map.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class BarterCategoryAdminListener implements Listener {

	private final BarterCategoryItemsAdminView categoryView;

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
