package org.luckyraven.gangland.events.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** TDD stub: a player left a gadget car (fired by gangland-gadget). */
public class CarExitEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	public CarExitEvent(Player player, Entity vehicle, boolean parked) {
		super(false);
	}

	public Player getPlayer() {
		return null;
	}

	public Entity getVehicle() {
		return null;
	}

	public boolean isParked() {
		return false;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		throw new UnsupportedOperationException("TDD stub");
	}

}
