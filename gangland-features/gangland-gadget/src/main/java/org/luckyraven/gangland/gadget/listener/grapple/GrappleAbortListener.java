package org.luckyraven.gangland.gadget.listener.grapple;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.luckyraven.gangland.gadget.grapple.GrappleService;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Aborts an in-progress grapple pull: damage, sneak-start (G2), and teleport, world-change and death (G3). The last
 * three end any session, a shot still in flight included ({@code cancel} is a no-op without one): the hook would
 * otherwise keep flying from where the player no longer is.
 * <p>
 * {@code onDamage} is deliberately {@code MONITOR} priority + {@code ignoreCancelled = true}: G3's
 * {@code GrappleFallDamageListener} will cancel {@code DamageCause.FALL} at {@code HIGH} priority while a pull is
 * active (the player is being mechanically moved, not really falling). Because {@code MONITOR} runs after
 * {@code HIGH}, a cancelled fall-immune landing during an active pull is already cancelled by the time this handler
 * runs and {@code ignoreCancelled = true} skips it — so a soft landing never aborts the pull as "damage". A genuine
 * non-fall hit still aborts it.
 */
@ListenerHandler
@AutowireTarget({GrappleService.class})
public class GrappleAbortListener implements Listener {

	private final GrappleService grappleService;

	public GrappleAbortListener(GrappleService grappleService) {
		this.grappleService = grappleService;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (!grappleService.isActive(player)) return;
		grappleService.cancel(player);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onToggleSneak(PlayerToggleSneakEvent event) {
		if (!event.isSneaking()) return;   // only the sneak-START cancels the pull
		Player player = event.getPlayer();
		if (!grappleService.isActive(player)) return;
		grappleService.cancel(player);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onTeleport(PlayerTeleportEvent event) {
		grappleService.cancel(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChangeWorld(PlayerChangedWorldEvent event) {
		grappleService.cancel(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		grappleService.cancel(event.getEntity());   // PlayerDeathEvent#getEntity() covariantly returns Player
	}

	/**
	 * Fix round 1 (F3): without this, {@code GrappleService}'s cooldown/landing-grace maps would keep one entry per
	 * player who has ever used a grapple for the plugin's entire uptime. Mirrors {@code CarQuitListener}/
	 * {@code JetpackActivateListener#onQuit}'s own quit-cleanup precedent — {@code forget} (not {@code cancel})
	 * because a quitting player isn't "landing" and shouldn't be granted a landing grace they'll never use.
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		grappleService.forget(event.getPlayer());
	}

}
