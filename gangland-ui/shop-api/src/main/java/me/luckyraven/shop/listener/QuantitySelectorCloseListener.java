package me.luckyraven.shop.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.listener.ListenerHandler;
import me.luckyraven.shop.view.QuantitySelectorView;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

@ListenerHandler
@RequiredArgsConstructor
public final class QuantitySelectorCloseListener implements Listener {

	private final QuantitySelectorView quantitySelectorView;

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) {
			return;
		}
		quantitySelectorView.handleClose(player, event.getInventory());
	}

}
