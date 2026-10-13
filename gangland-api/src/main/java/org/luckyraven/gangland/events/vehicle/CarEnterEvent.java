package org.luckyraven.gangland.events.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A player entered a gadget car (fired by gangland-gadget). Synchronous, not cancellable. */
public class CarEnterEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final Entity vehicle;
	private final String carType;

	public CarEnterEvent(Player player, Entity vehicle, String carType) {
		super(false);
		this.player = player;
		this.vehicle = vehicle;
		this.carType = carType;
	}

	public Player getPlayer() {
		return player;
	}

	public Entity getVehicle() {
		return vehicle;
	}

	public String getCarType() {
		return carType;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
