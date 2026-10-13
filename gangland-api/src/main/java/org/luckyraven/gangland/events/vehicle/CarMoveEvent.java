package org.luckyraven.gangland.events.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** TDD stub: a gadget car moved, throttled, not cancellable (fired by gangland-gadget). */
public class CarMoveEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	public CarMoveEvent(Player driver, Entity vehicle, Location from, Location to, double blocksPerSecond) {
		super(false);
	}

	public Player getDriver() {
		return null;
	}

	public Entity getVehicle() {
		return null;
	}

	public Location getFrom() {
		return null;
	}

	public Location getTo() {
		return null;
	}

	public double getBlocksPerSecond() {
		return 0;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		throw new UnsupportedOperationException("TDD stub");
	}

}
