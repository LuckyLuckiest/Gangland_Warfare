package org.luckyraven.gangland.events.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** TDD stub: a gadget car took damage, cancellable (fired by gangland-gadget). */
public class CarDamageEvent extends Event implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();

	public CarDamageEvent(Entity vehicle, @Nullable Entity damager, double damage) {
		super(false);
	}

	public Entity getVehicle() {
		return null;
	}

	public @Nullable Entity getDamager() {
		return null;
	}

	public double getDamage() {
		return 0;
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
