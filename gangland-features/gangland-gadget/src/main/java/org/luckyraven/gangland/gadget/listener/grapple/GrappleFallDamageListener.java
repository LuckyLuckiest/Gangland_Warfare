package org.luckyraven.gangland.gadget.listener.grapple;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.luckyraven.gangland.gadget.grapple.GrappleService;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Cancels fall damage for a grapple pull, through two independently time-bounded paths — deliberately shaped to
 * avoid docket GD-04 (an empty jetpack grants permanent fall immunity, {@code JetpackFallDamageListener}): that
 * listener cancels {@code DamageCause.FALL} whenever {@code JetpackService.isActive(player)} is true, and
 * jetpack "active" has no expiry of its own — an empty/zero-fuel jetpack stays active until the player manually
 * takes it off, so the immunity is effectively permanent for as long as it's worn.
 * <p>
 * Here, {@link GrappleService#isActive(Player)} is only true for the duration of a live pull, which is inherently
 * bounded — {@code Max_Duration_Ticks} forces it to end, and G2/G3 wire multiple independent cancel triggers
 * (damage, sneak, chunk-unload, teleport, world-change, death) on top of that timeout. There is no way to stay
 * "active" indefinitely the way an inert-but-worn jetpack can.
 * <p>
 * The post-pull grace ({@link GrappleService#consumeLandingGrace(Player)}) is intentionally a separate, one-shot
 * flag rather than an extension of {@code isActive}: {@code GrappleService.cancel(...)} grants it exactly once
 * per pull-end, and consuming it removes it from the map immediately, so it can absorb at most a single
 * fall-damage event no matter how long the player keeps holding the grapple item afterward — the opposite of
 * GD-04's unbounded, indefinitely re-usable immunity.
 */
@ListenerHandler
@AutowireTarget({GrappleService.class})
public class GrappleFallDamageListener implements Listener {

	private final GrappleService grappleService;

	public GrappleFallDamageListener(GrappleService grappleService) {
		this.grappleService = grappleService;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onFallDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

		if (grappleService.isActive(player)) {
			event.setCancelled(true);
			return;
		}
		if (grappleService.consumeLandingGrace(player)) {
			event.setCancelled(true);
		}
	}
}
