package me.luckyraven.gadget.listener.car;

import me.luckyraven.gadget.car.Car;
import me.luckyraven.gadget.car.CarKey;
import me.luckyraven.gadget.car.CarService;
import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.item.fuel.Fuel;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles right-clicking a car item on a block to place the vehicle in the world. The player must then right-click the
 * entity to mount it (see {@link CarEntityInteractListener}).
 */
@ListenerHandler
@AutowireTarget({CarService.class})
public class CarInteractListener implements Listener {

	private final CarService carService;

	public CarInteractListener(CarService carService) {
		this.carService = carService;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		if (event.getHand() != EquipmentSlot.HAND) return;

		Player    player = event.getPlayer();
		ItemStack item   = player.getInventory().getItemInMainHand();

		String carId = Car.getCarId(item);
		if (carId == null) return;

		Car car = carService.getCarManager().getCar(carId);
		if (car == null) return;

		event.setCancelled(true);

		// Permission check
		String permission = car.getPermission();
		if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
			player.sendMessage(ChatUtil.color("&cYou do not have permission to use this car."));
			return;
		}

		// Find the spawn location on top of the clicked block
		Block clicked = event.getClickedBlock();
		if (clicked == null) return;

		Location spawnLoc = clicked.getRelative(BlockFace.UP).getLocation().add(0.5, 0, 0.5);

		FuelService fuelService = carService.getFuelService();

		// Read saved fuel from the item's NBT so values persist across sessions.
		// For a brand-new car (no NBT), default to the fuel definition's maximum capacity.
		ItemBuilder itemBuilder = new ItemBuilder(item);
		int         fuel;
		if (itemBuilder.hasNBTTag(CarKey.CAR_FUEL.getKey())) {
			fuel = itemBuilder.getIntegerTagData(CarKey.CAR_FUEL.getKey());
		} else if (car.isFuelEnabled() && car.getFuelKey() != null) {
			Fuel fuelDef = fuelService.getFuel(car.getFuelKey());
			fuel = fuelDef != null ? fuelDef.getMaxFuel() : 0;
		} else {
			fuel = 0;
		}
		int durability = itemBuilder.hasNBTTag(CarKey.CAR_DURABILITY.getKey()) ?
		                 itemBuilder.getIntegerTagData(CarKey.CAR_DURABILITY.getKey()) :
		                 car.getMaxDurability();

		boolean placed = carService.placeCar(player, carId, spawnLoc, fuel, durability);

		if (placed) {
			if (item.getAmount() > 1) {
				item.setAmount(item.getAmount() - 1);
			} else {
				player.getInventory().setItemInMainHand(null);
			}
		}
	}
}
