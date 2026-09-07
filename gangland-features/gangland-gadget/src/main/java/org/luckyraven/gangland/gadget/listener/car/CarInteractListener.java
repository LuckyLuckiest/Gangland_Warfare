package org.luckyraven.gangland.gadget.listener.car;

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
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarKey;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.ExhaustSide;
import org.luckyraven.gangland.gadget.car.message.CarMessageContract;
import org.luckyraven.gangland.gadget.fuel.FuelService;
import org.luckyraven.gangland.item.fuel.Fuel;
import org.luckyraven.gangland.item.fuel.FuelKey;

/**
 * Handles right-clicking a car item on a block to place the vehicle in the world. The player must then right-click the
 * entity to mount it (see {@link CarEntityInteractListener}).
 */
@ListenerHandler
@AutowireTarget({CarService.class, CarMessageContract.class})
public class CarInteractListener implements Listener {

	private final CarService         carService;
	private final CarMessageContract messages;

	public CarInteractListener(CarService carService, CarMessageContract messages) {
		this.carService = carService;
		this.messages   = messages;
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
		if (!player.hasPermission(car.getPermission())) {
			player.sendMessage(messages.noPermission());
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
		int         maxFuel;
		if (itemBuilder.hasNBTTag(FuelKey.FUEL_CURRENT.getKey())) {
			fuel = itemBuilder.getIntegerTagData(FuelKey.FUEL_CURRENT.getKey());
		} else if (car.isFuelEnabled() && car.getFuelKey() != null) {
			Fuel fuelDef = fuelService.getFuel(car.getFuelKey());
			fuel = fuelDef != null ? fuelDef.getMaxFuel() : 0;
		} else {
			fuel = 0;
		}
		if (itemBuilder.hasNBTTag(FuelKey.FUEL_MAX.getKey())) {
			maxFuel = itemBuilder.getIntegerTagData(FuelKey.FUEL_MAX.getKey());
		} else {
			maxFuel = car.getMaxFuel();
		}
		int durability = itemBuilder.hasNBTTag(CarKey.CAR_DURABILITY.getKey()) ?
		                 itemBuilder.getIntegerTagData(CarKey.CAR_DURABILITY.getKey()) :
		                 car.getMaxDurability();

		ExhaustSide exhaustSide = itemBuilder.hasNBTTag(CarKey.CAR_EXHAUST_SIDE.getKey())
		                          ?
		                          ExhaustSide.fromString(itemBuilder.getStringTagData(CarKey.CAR_EXHAUST_SIDE.getKey()))
		                          :
		                          null;

		boolean placed = carService.placeCar(player, carId, spawnLoc, fuel, maxFuel, durability, exhaustSide);

		if (placed) {
			if (item.getAmount() > 1) {
				item.setAmount(item.getAmount() - 1);
			} else {
				player.getInventory().setItemInMainHand(null);
			}
		}
	}
}
