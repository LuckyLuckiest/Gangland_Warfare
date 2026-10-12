package org.luckyraven.gangland.turf.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A guard deployed for a turf died (not expired, not recalled). Fired by TurfDefenderDeployer once per tick in which at
 * least one of the turf's guards went down. remaining = guards of the turf still alive, deployed = guards deployed for
 * the turf since its group was created (the size to compare "half the guards" against).
 */
public final class TurfDefenderDownEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final int turfId;
	private final int remaining;
	private final int deployed;

	public TurfDefenderDownEvent(int turfId, int remaining, int deployed) {
		this.turfId    = turfId;
		this.remaining = remaining;
		this.deployed  = deployed;
	}

	public int turfId() {
		return turfId;
	}

	public int remaining() {
		return remaining;
	}

	public int deployed() {
		return deployed;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
