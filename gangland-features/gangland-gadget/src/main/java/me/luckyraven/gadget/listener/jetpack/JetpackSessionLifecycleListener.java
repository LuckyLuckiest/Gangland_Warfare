package me.luckyraven.gadget.listener.jetpack;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.autowire.AutowireTarget;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.core.downed.PlayerUndownedEvent;
import me.luckyraven.gadget.jetpack.JetpackService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;

/**
 * Manages jetpack session lifecycle around vehicle mount/dismount and GTA-style respawn (undowned). Deactivates the
 * jetpack when the player enters any vehicle, and re-checks the chestplate slot when the player exits a vehicle or
 * recovers from the downed state.
 */
@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({JetpackService.class})
public class JetpackSessionLifecycleListener implements Listener {

	private final JetpackService jetpackService;

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onMount(EntityMountEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (!jetpackService.isActive(player)) return;

		jetpackService.deactivate(player);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDismount(EntityDismountEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;

		jetpackService.scheduleChestplateCheck(player);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onUndowned(PlayerUndownedEvent event) {
		jetpackService.scheduleChestplateCheck(event.getPlayer());
	}
}
