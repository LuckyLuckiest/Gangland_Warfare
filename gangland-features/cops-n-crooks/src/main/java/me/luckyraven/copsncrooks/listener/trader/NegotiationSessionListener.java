package me.luckyraven.copsncrooks.listener.trader;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.view.NegotiationView;
import me.luckyraven.core.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Singleton close-event listener for the trader negotiation view. Delegates back to {@link NegotiationView} which owns
 * the per-player session map.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class NegotiationSessionListener implements Listener {

	private final NegotiationView negotiationView;

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) return;
		negotiationView.handleClose(player, event.getInventory());
	}

}
