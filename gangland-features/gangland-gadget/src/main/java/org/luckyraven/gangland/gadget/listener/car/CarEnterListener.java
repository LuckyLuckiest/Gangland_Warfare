package org.luckyraven.gangland.gadget.listener.car;

import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Republishes a player entering a gadget car as the api {@code CarEnterEvent}. Read-only observer: MONITOR,
 * ignoreCancelled, never cancels, so cops DetainmentListener and gang FightContainmentListener are untouched.
 */
@ListenerHandler
@AutowireTarget({CarService.class})
public class CarEnterListener implements Listener {

	private final CarService carService;

	public CarEnterListener(CarService carService) {
		this.carService = carService;
	}

	public void onEnter(VehicleEnterEvent event) {
		// TDD stub: no behaviour
	}

}
