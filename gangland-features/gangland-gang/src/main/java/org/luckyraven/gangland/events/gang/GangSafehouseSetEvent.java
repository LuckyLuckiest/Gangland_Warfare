package org.luckyraven.gangland.events.gang;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A gang is about to set a safehouse at a location. Cancelling stops it. Fired by the safehouse command. */
public class GangSafehouseSetEvent extends Event implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	private final int      gangId;
	private final Location location;
	private final String   name;

	private boolean cancelled;

	public GangSafehouseSetEvent(int gangId, Location location, String name) {
		this.gangId   = gangId;
		this.location = location;
		this.name     = name;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public int gangId() {
		return gangId;
	}

	public Location location() {
		return location;
	}

	public String name() {
		return name;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
}
