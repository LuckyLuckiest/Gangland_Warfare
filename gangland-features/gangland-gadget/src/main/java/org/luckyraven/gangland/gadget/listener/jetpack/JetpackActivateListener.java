package org.luckyraven.gangland.gadget.listener.jetpack;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.core.bean.autowire.AutowireTarget;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.gadget.jetpack.JetpackService;

@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({JetpackService.class})
public class JetpackActivateListener implements Listener {

	private final JetpackService jetpackService;

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		jetpackService.scheduleChestplateCheck(event.getPlayer());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		if (!jetpackService.isActive(player)) return;
		jetpackService.deactivate(player);
	}

}
