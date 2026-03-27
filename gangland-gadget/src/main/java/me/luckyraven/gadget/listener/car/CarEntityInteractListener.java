package me.luckyraven.gadget.listener.car;

import me.luckyraven.gadget.car.CarService;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/**
 * Handles interaction with a placed (parked) car entity. Right-clicking the entity (sneaking or not) mounts the player
 * into the vehicle. Pickup is handled via shift + left-click in {@link CarDamageListener}.
 */
@ListenerHandler
@AutowireTarget({CarService.class})
public class CarEntityInteractListener implements Listener {

	private final CarService carService;

	public CarEntityInteractListener(CarService carService) {
		this.carService = carService;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onInteractEntity(PlayerInteractEntityEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;

		Player player     = event.getPlayer();
		Entity clicked    = event.getRightClicked();
		UUID   entityUUID = clicked.getUniqueId();

		if (!carService.isParkedVehicle(entityUUID)) return;

		event.setCancelled(true);

		if (carService.getVehicleRegistry().isPlayerDriving(player.getUniqueId())) {
			player.sendMessage(ChatUtil.color("&cYou are already driving a vehicle."));
			return;
		}
		carService.mountCar(player, entityUUID);
	}
}
