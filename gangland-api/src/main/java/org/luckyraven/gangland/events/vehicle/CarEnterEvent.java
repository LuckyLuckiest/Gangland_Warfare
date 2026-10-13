package org.luckyraven.gangland.events.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** TDD stub: a player entered a gadget car (fired by gangland-gadget). */
public class CarEnterEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	public CarEnterEvent(Player player, Entity vehicle, String carType) {
		super(false);
	}

	public Player getPlayer() {
		return null;
	}

	public Entity getVehicle() {
		return null;
	}

	public String getCarType() {
		return null;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		throw new UnsupportedOperationException("TDD stub");
	}

}
