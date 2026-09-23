package org.luckyraven.gangland.gadget.listener.jetpack;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.core.downed.PlayerUndownedEvent;
import org.luckyraven.gangland.gadget.jetpack.JetpackService;

/**
 * Manages jetpack session lifecycle around vehicle mount/dismount, entering the downed state, and GTA-style
 * respawn (undowned). Deactivates the jetpack when the player enters any vehicle or is downed, and re-checks the
 * chestplate slot when the player exits a vehicle or recovers from the downed state.
 *
 * <p>Listens to the {@code org.bukkit.event.vehicle} events rather than {@code EntityMountEvent}/
 * {@code EntityDismountEvent}: those live in {@code org.spigotmc} on the 1.16.5 compile floor but in
 * {@code org.bukkit} from 1.20.3, so no single class name compiles against the floor and loads on both.
 */
@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({JetpackService.class})
public class JetpackSessionLifecycleListener implements Listener {

	private final JetpackService jetpackService;

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onMount(VehicleEnterEvent event) {
		if (!(event.getEntered() instanceof Player player)) return;
		if (!jetpackService.isActive(player)) return;

		jetpackService.deactivate(player);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDismount(VehicleExitEvent event) {
		if (!(event.getExited() instanceof Player player)) return;

		jetpackService.scheduleChestplateCheck(player);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onUndowned(PlayerUndownedEvent event) {
		jetpackService.scheduleChestplateCheck(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDowned(PlayerDownedEvent event) {
		jetpackService.deactivate(event.getPlayer());
	}
}
