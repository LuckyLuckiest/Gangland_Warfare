package me.luckyraven.gadget.listener.car;

import me.luckyraven.core.bean.autowire.AutowireTarget;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.core.downed.DownedPlayerRegistry;
import me.luckyraven.gadget.car.CarService;
import me.luckyraven.gadget.car.vehicle.VehicleSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;

/**
 * Handles the player exiting a car vehicle. Parks the vehicle in the world (keeps the entity alive) so the player can
 * later right-click to remount or shift+right-click to pick it up. Teleports the player to a safe exit position to
 * prevent block-clipping on dismount.
 */
@ListenerHandler
@AutowireTarget({CarService.class})
public class CarDismountListener implements Listener {

	private final CarService carService;

	public CarDismountListener(CarService carService) {
		this.carService = carService;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onDismount(EntityDismountEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;

		Entity vehicle = event.getDismounted();

		VehicleSession session = carService.getVehicleRegistry().getByEntity(vehicle.getUniqueId());
		if (session == null) return;

		// Cancel dismounts that were not triggered by the player pressing shift (e.g. Minecraft
		// physics ejecting the player from a no-rail minecart). Death-caused dismounts are still
		// allowed through so the player can respawn normally.
		if (!player.isSneaking() && !player.isDead() && !DownedPlayerRegistry.isDowned(player.getUniqueId())) {
			event.setCancelled(true);
			return;
		}

		// Capture safe exit location before any entity manipulation
		Location safeExit = findSafeExitLocation(vehicle.getLocation());

		carService.parkCar(vehicle.getUniqueId());

		// Teleport one tick later so the dismount packet is fully processed first
		Bukkit.getScheduler().runTaskLater(carService.getPlugin(), () -> {
			if (player.isOnline()) {
				player.teleport(safeExit);
			}
		}, 1L);
	}

	/**
	 * Scans upward from the minecart position until a 2-block-tall gap is found. Falls back to 1 block above base if
	 * nothing clear is found within 5 blocks.
	 */
	private Location findSafeExitLocation(Location base) {
		World world = base.getWorld();
		if (world == null) {
			return base.clone().add(0, 1, 0);
		}

		for (int dy = 0; dy <= 5; dy++) {
			Location candidate = base.clone().add(0, dy, 0);
			Block    feet      = world.getBlockAt(candidate);
			Block    head      = world.getBlockAt(candidate.clone().add(0, 1, 0));
			if (feet.isPassable() && head.isPassable()) {
				return candidate;
			}
		}

		return base.clone().add(0, 1, 0);
	}
}
