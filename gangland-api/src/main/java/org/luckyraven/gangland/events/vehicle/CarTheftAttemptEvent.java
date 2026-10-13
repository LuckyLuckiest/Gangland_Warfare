package org.luckyraven.gangland.events.vehicle;

import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A player attempts to hotwire a gadget car, cancel = no hotwire (fired by gangland-gadget). The owner may be null. */
public class CarTheftAttemptEvent extends Event implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player thief;
	private final Entity vehicle;
	private final @Nullable UUID owner;
	private boolean cancelled;

	public CarTheftAttemptEvent(Player thief, Entity vehicle, @Nullable UUID owner) {
		super(false);
		this.thief = thief;
		this.vehicle = vehicle;
		this.owner = owner;
	}

	public Player getThief() {
		return thief;
	}

	public Entity getVehicle() {
		return vehicle;
	}

	public @Nullable UUID getOwner() {
		return owner;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
