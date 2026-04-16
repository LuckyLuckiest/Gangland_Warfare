package me.luckyraven.shop.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.shop.view.PriceEditorView;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

@ListenerHandler
@RequiredArgsConstructor
public final class PriceEditorCloseListener implements Listener {

	private final PriceEditorView priceEditorView;

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) {
			return;
		}
		priceEditorView.handleClose(player, event.getInventory());
	}

}
