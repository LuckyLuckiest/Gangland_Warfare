package org.luckyraven.gangland.events.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A gadget car took damage, cancellable (fired by gangland-gadget). The damager may be null. */
public class CarDamageEvent extends Event implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Entity vehicle;
	private final @Nullable Entity damager;
	private final double damage;
	private boolean cancelled;

	public CarDamageEvent(Entity vehicle, @Nullable Entity damager, double damage) {
		super(false);
		this.vehicle = vehicle;
		this.damager = damager;
		this.damage = damage;
	}

	public Entity getVehicle() {
		return vehicle;
	}

	public @Nullable Entity getDamager() {
		return damager;
	}

	public double getDamage() {
		return damage;
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
