package org.luckyraven.gangland.turf.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * Fired when a contest cancels without completing — either the challenger abandoned the area past the grace window
 * (ABANDONED), an admin cancelled the contest (CANCELLED), or defenders clawed progress back to zero (DEFENDED).
 *
 * <p>The spec explicitly says failed captures stay quiet — only wins get a
 * broadcast. This event exists for logging / listener bookkeeping only.
 */
@Getter
public final class TurfCaptureFailedEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Turf   turf;
	private final Reason reason;

	public TurfCaptureFailedEvent(Turf turf, Reason reason) {
		this.turf   = turf;
		this.reason = reason;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public enum Reason {
		ABANDONED,
		CANCELLED,
		DEFENDED
	}
}
