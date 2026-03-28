package me.luckyraven.inventory.listener;

import me.luckyraven.inventory.service.InventoryRegistry;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@ListenerHandler
public class PlayerInventoryCleanup implements Listener {

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		InventoryRegistry.getInstance().clear(event.getPlayer().getUniqueId());
	}

}
