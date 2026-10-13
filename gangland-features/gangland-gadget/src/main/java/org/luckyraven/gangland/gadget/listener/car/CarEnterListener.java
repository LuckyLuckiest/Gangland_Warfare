package org.luckyraven.gangland.gadget.listener.car;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.luckyraven.gangland.events.vehicle.CarEnterEvent;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.vehicle.ParkedVehicle;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleSession;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.UUID;

/**
 * Republishes a player entering a gadget car as the api {@code CarEnterEvent}. Read-only observer: MONITOR,
 * ignoreCancelled, never cancels, so cops DetainmentListener and gang FightContainmentListener are untouched.
 */
@CustomLog
@ListenerHandler
@AutowireTarget({CarService.class})
public class CarEnterListener implements Listener {

	private final CarService carService;

	public CarEnterListener(CarService carService) {
		this.carService = carService;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEnter(VehicleEnterEvent event) {
		if (!(event.getEntered() instanceof Player player)) {
			return;
		}

		String type = carType(event.getVehicle().getUniqueId());
		if (type == null) {
			return;
		}

		Bukkit.getPluginManager().callEvent(new CarEnterEvent(player, event.getVehicle(), type));
		log.debug("CAR_EVENT enter {} car={}", player.getName(), type);
	}

	private String carType(UUID id) {
		VehicleSession session = carService.getVehicleRegistry().getByEntity(id);
		if (session != null) {
			return session.getCar().getCarId();
		}

		ParkedVehicle parked = carService.getParkedVehicle(id);
		if (parked != null) {
			Car car = parked.getCar();
			return car.getCarId();
		}

		return null;
	}

}
