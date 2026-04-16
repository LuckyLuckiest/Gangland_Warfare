package me.luckyraven.shop.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.shop.view.ShopAdminView;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

@ListenerHandler
@RequiredArgsConstructor
public final class ShopAdminListener implements Listener {

	private final ShopAdminView shopAdminView;

	@EventHandler(priority = EventPriority.HIGH)
	public void onClick(InventoryClickEvent event) {
		shopAdminView.handleClick(event);
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) {
			return;
		}
		shopAdminView.handleClose(player, event.getInventory());
	}

}
