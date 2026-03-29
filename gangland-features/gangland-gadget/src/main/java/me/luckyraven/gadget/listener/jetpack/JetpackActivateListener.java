package me.luckyraven.gadget.listener.jetpack;

import me.luckyraven.gadget.jetpack.JetpackService;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cleans up the jetpack session when a player disconnects.
 */
@ListenerHandler
@AutowireTarget({JetpackService.class})
public class JetpackActivateListener implements Listener {

	private final JetpackService jetpackService;

	public JetpackActivateListener(JetpackService jetpackService) {
		this.jetpackService = jetpackService;
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		if (!jetpackService.isActive(player)) return;
		jetpackService.deactivate(player);
	}

}
