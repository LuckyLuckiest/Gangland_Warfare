package me.luckyraven.copsncrooks.listener.trader;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.view.SellBargainView;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Singleton close-event listener for the sell-side bargain sub-view. Delegates to {@link SellBargainView} so all
 * session-lookup state stays inside the view bean.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class SellBargainCloseListener implements Listener {

	private final SellBargainView sellBargainView;

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) return;
		sellBargainView.handleClose(player, event.getInventory());
	}

}
