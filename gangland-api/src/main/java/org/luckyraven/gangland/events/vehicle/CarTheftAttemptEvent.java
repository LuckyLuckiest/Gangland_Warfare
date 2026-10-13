package org.luckyraven.gangland.events.vehicle;

import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** TDD stub: a player attempts to hotwire a gadget car, cancel = no hotwire (fired by gangland-gadget). */
public class CarTheftAttemptEvent extends Event implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();

	public CarTheftAttemptEvent(Player thief, Entity vehicle, @Nullable UUID owner) {
		super(false);
	}

	public Player getThief() {
		return null;
	}

	public Entity getVehicle() {
		return null;
	}

	public @Nullable UUID getOwner() {
		return null;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public void setCancelled(boolean cancel) {
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		throw new UnsupportedOperationException("TDD stub");
	}

}
