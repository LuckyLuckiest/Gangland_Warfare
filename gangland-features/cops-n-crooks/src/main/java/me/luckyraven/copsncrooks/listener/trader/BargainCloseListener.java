package me.luckyraven.copsncrooks.listener.trader;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.view.BargainView;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Singleton close-event listener for the buy-side bargain sub-view. Delegates back to {@link BargainView} so all
 * session-lookup state stays inside the view bean — this class is a pure adapter from the Bukkit event bus.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class BargainCloseListener implements Listener {

	private final BargainView bargainView;

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) return;
		bargainView.handleClose(player, event.getInventory());
	}

}
