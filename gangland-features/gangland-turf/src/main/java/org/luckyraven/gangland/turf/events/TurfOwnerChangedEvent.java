package org.luckyraven.gangland.turf.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * Fired for EVERY change of a turf's owning gang, captures included: a capture fires this event first, then
 * {@link TurfCapturedEvent}. Admin {@code setowner}, delete, gang disband, the inactivity sweep and the orphan
 * releases all fire it too, so boss-bar, guard and sound listeners refresh without polling. {@link #getCause()} says
 * why the owner changed. Fired only when the owner actually changes; a same-owner write fires nothing.
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
	private final Cause   cause;

	public TurfOwnerChangedEvent(Turf turf,
	                             @Nullable Integer oldOwnerGangId,
	                             @Nullable Integer newOwnerGangId,
	                             Cause cause) {
		this.turf           = turf;
		this.oldOwnerGangId = oldOwnerGangId;
		this.newOwnerGangId = newOwnerGangId;
		this.cause          = cause;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	/**
	 * Why the owner changed.
	 */
	public enum Cause {
		/** A capture completed. */
		CAPTURE,
		/** {@code /glw turf setowner}. */
		ADMIN,
		/** Owner gang inactive past {@code Capture.Inactivity_Auto_Release_Days}. */
		INACTIVITY,
		/** Owner gang id no longer exists (found by a task). */
		ORPHANED,
		/** {@code GangDeleteEvent}. */
		GANG_DISBANDED
	}
}
