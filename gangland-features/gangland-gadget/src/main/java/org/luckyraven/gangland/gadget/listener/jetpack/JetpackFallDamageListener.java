package org.luckyraven.gangland.gadget.listener.jetpack;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.gadget.jetpack.JetpackService;
import org.luckyraven.gangland.gadget.jetpack.JetpackTask;

/**
 * Cancels fall damage for players with an active jetpack session. The predictive ground-braking in {@link JetpackTask}
 * will usually land the player softly, but this listener acts as a safety net for edge cases (e.g., sudden terrain
 * changes or server lag spikes).
 */
@ListenerHandler
@AutowireTarget({JetpackService.class})
public class JetpackFallDamageListener implements Listener {

	private final JetpackService jetpackService;

	public JetpackFallDamageListener(JetpackService jetpackService) {
		this.jetpackService = jetpackService;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onFallDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
		if (jetpackService.isActive(player)) {
			event.setCancelled(true);
		}
	}

}
