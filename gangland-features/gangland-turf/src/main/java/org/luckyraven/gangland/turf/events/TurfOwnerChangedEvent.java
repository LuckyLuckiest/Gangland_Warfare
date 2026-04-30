package org.luckyraven.gangland.turf.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * Fired when a turf's owning gang changes outside the capture flow — admin {@code /glw turf setowner}, inactivity
 * release, or any other non-capture mutation. Capture-driven changes still go through {@link TurfCapturedEvent}; this
 * event covers the owned→unclaimed transition (which capture cannot produce) and admin-initiated reassignments so
 * presence-bar / NPC / lifecycle listeners can refresh without polling.
 *
 * <p>{@link #getOldOwnerGangId()} is {@code null} when the turf was previously unclaimed; {@link #getNewOwnerGangId()}
 * is {@code null} when the turf has just been cleared.
 */
@Getter
public final class TurfOwnerChangedEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Turf    turf;
	private final Integer oldOwnerGangId;
	private final Integer newOwnerGangId;

	public TurfOwnerChangedEvent(Turf turf, @Nullable Integer oldOwnerGangId, @Nullable Integer newOwnerGangId) {
		this.turf           = turf;
		this.oldOwnerGangId = oldOwnerGangId;
		this.newOwnerGangId = newOwnerGangId;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
