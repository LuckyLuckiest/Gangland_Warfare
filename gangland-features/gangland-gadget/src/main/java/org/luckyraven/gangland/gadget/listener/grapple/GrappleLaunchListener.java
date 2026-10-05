package org.luckyraven.gangland.gadget.listener.grapple;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.luckyraven.gangland.gadget.grapple.Grapple;
import org.luckyraven.gangland.gadget.grapple.GrappleService;
import org.luckyraven.gangland.gadget.grapple.GrappleSession;
import org.luckyraven.gangland.gadget.grapple.config.GrappleAddon;
import org.luckyraven.gangland.gadget.grapple.message.GrappleMessages;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Fires and releases a grapple off the vanilla fishing-rod clicks. A grapple item is a fishing rod under the hood, so
 * the first right-click casts a hook ({@link PlayerFishEvent.State#FISHING}), which {@link GrappleService#fire} turns
 * into a fast web-shot; the rendered fishing line comes for free. The next right-click (reel, in-ground or a catch)
 * on that same hook lets go, found by hook identity so it works whichever hand holds the rod. A bite, a failed
 * attempt or any state newer servers add (LURED) is ignored. Never reads {@code event.getHand()} — absent on this
 * plugin's Spigot API floor; the cast looks up the main hand, matching {@code CarInteractListener}'s convention.
 */
@ListenerHandler
@AutowireTarget({GrappleService.class, GrappleAddon.class, GrappleMessages.class})
public class GrappleLaunchListener implements Listener {

	private final GrappleService  grappleService;
	private final GrappleAddon    grappleAddon;
	private final GrappleMessages grappleMessages;

	public GrappleLaunchListener(GrappleService grappleService, GrappleAddon grappleAddon,
	                             GrappleMessages grappleMessages) {
		this.grappleService  = grappleService;
		this.grappleAddon    = grappleAddon;
		this.grappleMessages = grappleMessages;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerFish(PlayerFishEvent event) {
		Player player = event.getPlayer();
		switch (event.getState()) {
			case FISHING -> cast(event, player);
			case REEL_IN, IN_GROUND, CAUGHT_FISH, CAUGHT_ENTITY -> release(event, player);
			default -> {
				// BITE (a fish nibbling at a hook pinned in water), FAILED_ATTEMPT, LURED on newer servers: not a click
			}
		}
	}

	/**
	 * Lets go, keeping momentum, if this is the session's own hook. Cancelled so vanilla never fishes, yanks a hooked
	 * entity or wears the rod; the hook goes with us.
	 */
	private void release(PlayerFishEvent event, Player player) {
		GrappleSession session = grappleService.getSession(player);
		if (session == null || !session.getHook().equals(event.getHook())) return;

		event.setCancelled(true);
		event.getHook().remove();
		grappleService.cancel(player);
	}

	private void cast(PlayerFishEvent event, Player player) {
		String id = Grapple.getGrappleId(player.getInventory().getItemInMainHand());
		if (id == null) return;   // not a grapple item — leave vanilla fishing untouched

		Grapple grapple = grappleAddon.getGrapple(id);
		if (grapple == null) return;

		if (!player.hasPermission(grapple.getPermission())) {
			player.sendMessage(grappleMessages.noPermission());
			event.setCancelled(true);
			return;
		}
		// Cooldown or an existing session: no hook at all. No message — the player already knows.
		if (!grappleService.fire(player, grapple, event.getHook())) {
			event.setCancelled(true);
		}
	}
}
