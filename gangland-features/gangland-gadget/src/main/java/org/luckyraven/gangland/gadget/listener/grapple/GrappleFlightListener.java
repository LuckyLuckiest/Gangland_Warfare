package org.luckyraven.gangland.gadget.listener.grapple;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.luckyraven.gangland.gadget.grapple.GrappleService;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * The allow-flight a taut grapple rope lends exists only so a hanging player is not kicked for floating: starting to
 * fly on it is cancelled. A join hands the player to {@link GrappleService#healFlight}, which takes back flight a crash
 * mid-swing left behind.
 */
@ListenerHandler
@AutowireTarget({GrappleService.class})
public class GrappleFlightListener implements Listener {

	private final GrappleService grappleService;

	public GrappleFlightListener(GrappleService grappleService) {
		this.grappleService = grappleService;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onToggleFlight(PlayerToggleFlightEvent event) {
		if (!event.isFlying()) return;
		if (!grappleService.holdsFlight(event.getPlayer())) return;
		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onJoin(PlayerJoinEvent event) {
		grappleService.healFlight(event.getPlayer());
	}
}
