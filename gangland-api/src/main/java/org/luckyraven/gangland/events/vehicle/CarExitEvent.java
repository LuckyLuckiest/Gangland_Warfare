package org.luckyraven.gangland.events.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A player left a gadget car (fired by gangland-gadget). Synchronous, not cancellable. */
public class CarExitEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final Entity vehicle;
	private final boolean parked;

	public CarExitEvent(Player player, Entity vehicle, boolean parked) {
		super(false);
		this.player = player;
		this.vehicle = vehicle;
		this.parked = parked;
	}

	public Player getPlayer() {
		return player;
	}

	public Entity getVehicle() {
		return vehicle;
	}

	public boolean isParked() {
		return parked;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
