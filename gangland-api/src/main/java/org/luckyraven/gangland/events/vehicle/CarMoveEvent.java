package org.luckyraven.gangland.events.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A gadget car moved, throttled, not cancellable (fired by gangland-gadget). Locations are defensively cloned. */
public class CarMoveEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player driver;
	private final Entity vehicle;
	private final Location from;
	private final Location to;
	private final double blocksPerSecond;

	public CarMoveEvent(Player driver, Entity vehicle, Location from, Location to, double blocksPerSecond) {
		super(false);
		this.driver = driver;
		this.vehicle = vehicle;
		this.from = from.clone();
		this.to = to.clone();
		this.blocksPerSecond = blocksPerSecond;
	}

	public Player getDriver() {
		return driver;
	}

	public Entity getVehicle() {
		return vehicle;
	}

	public Location getFrom() {
		return from.clone();
	}

	public Location getTo() {
		return to.clone();
	}

	public double getBlocksPerSecond() {
		return blocksPerSecond;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
