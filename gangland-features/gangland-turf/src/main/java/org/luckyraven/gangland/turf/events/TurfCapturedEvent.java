package org.luckyraven.gangland.turf.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * Fired when a capture completes — the turf's {@code ownerGangId} has just flipped from the old owner to the new one
 * and the cooldown has started.
 */
@Getter
public final class TurfCapturedEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final           Turf turf;
	private final @Nullable Gang oldOwner;
	private final           Gang newOwner;

	public TurfCapturedEvent(Turf turf, @Nullable Gang oldOwner, Gang newOwner) {
		this.turf     = turf;
		this.oldOwner = oldOwner;
		this.newOwner = newOwner;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
