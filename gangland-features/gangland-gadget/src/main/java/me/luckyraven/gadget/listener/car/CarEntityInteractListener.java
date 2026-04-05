package me.luckyraven.gadget.listener.car;

import me.luckyraven.gadget.car.CarService;
import me.luckyraven.gadget.car.vehicle.ParkedVehicle;
import me.luckyraven.item.fuel.Fuel;
import me.luckyraven.item.fuel.FuelBar;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ActionBarManager;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles interaction with a placed (parked) car entity. Plain right-click mounts the player. Shift + right-click is
 * intentionally ignored — pickup is handled by shift + left-click via {@link CarDamageListener}.
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

		// Shift + right-click is reserved for other actions (pickup = shift + left-click)
		if (player.isSneaking()) return;

		// Fuel-can refueling: right-click the car while holding a matching fuel can
		ItemStack heldItem = player.getInventory().getItemInMainHand();
		if (Fuel.isFuelItem(heldItem)) {
			String        heldFuelKey = Fuel.getFuelKey(heldItem);
			ParkedVehicle parked      = carService.getParkedVehicle(entityUUID);

			if (parked != null && parked.getCar().isFuelEnabled() && heldFuelKey != null &&
			    heldFuelKey.equals(parked.getCar().getFuelKey())) {

				int canFuel = Fuel.getCurrentFuel(heldItem);
				if (canFuel <= 0) {
					ActionBarManager.send(player, "&cFuel can is empty!");
					return;
				}

				int maxCarFuel     = parked.getCar().getMaxFuel();
				int currentCarFuel = parked.getFuel();
				int spaceInCar     = maxCarFuel - currentCarFuel;

				if (spaceInCar <= 0) {
					ActionBarManager.send(player, "&cCar fuel is already full!");
					return;
				}

				int toTransfer = Math.min(canFuel, spaceInCar);
				if (!carService.refuelParkedCar(entityUUID, toTransfer)) {
					ActionBarManager.send(player, "&cCould not refuel the car!");
					return;
				}

				int newCarFuel  = parked.getFuel();
				int actualAdded = newCarFuel - currentCarFuel;
				player.getInventory().setItemInMainHand(Fuel.setCurrentFuel(heldItem, canFuel - actualAdded));

				player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
				ActionBarManager.send(player, FuelBar.render(newCarFuel, maxCarFuel));
				return;
			}
		}

		if (carService.getVehicleRegistry().isPlayerDriving(player.getUniqueId())) {
			player.sendMessage(ChatUtil.color("&cYou are already driving a vehicle."));
			return;
		}
		carService.mountCar(player, entityUUID);
	}
}
