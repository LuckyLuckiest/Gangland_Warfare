package me.luckyraven.gadget.listener.jetpack;

import me.luckyraven.gadget.jetpack.JetpackService;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Cancels fall damage for players with an active jetpack session. The predictive ground-braking in
 * {@link me.luckyraven.gadget.jetpack.JetpackTask} will usually land the player softly, but this listener acts as a
 * safety net for edge cases (e.g., sudden terrain changes or server lag spikes).
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
