package me.luckyraven.shop.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.shop.view.SellCategoryItemsAdminView;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Singleton listener for the sell-category admin editor. Delegates every click to {@link SellCategoryItemsAdminView}
 * so cursor-drops, shift-clicks, and per-slot left/right clicks can all be handled in one place.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class SellCategoryAdminListener implements Listener {

	private final SellCategoryItemsAdminView categoryView;

	@EventHandler(priority = EventPriority.HIGH)
	public void onClick(InventoryClickEvent event) {
		categoryView.handleClick(event);
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player admin)) return;
		categoryView.handleClose(admin, event.getInventory());
	}

}
