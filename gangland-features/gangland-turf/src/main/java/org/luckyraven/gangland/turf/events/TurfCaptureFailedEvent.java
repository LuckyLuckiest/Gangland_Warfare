package org.luckyraven.gangland.turf.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * Fired when a contest cancels without completing — either the challenger abandoned the area past the grace window
 * (ABANDONED), an admin cancelled the contest (CANCELLED), or defenders clawed progress back to zero (DEFENDED).
 *
 * <p>W2-N1 tells both sides (held, stalled, failed). {@link #getChallengerGangId()} is the attacking gang (null when
 * none was known).
 */
@Getter
public final class TurfCaptureFailedEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Turf   turf;
	private final Reason reason;
	@Nullable
	private final Integer challengerGangId;

	public TurfCaptureFailedEvent(Turf turf, Reason reason) {
		this(turf, reason, null);
	}

	public TurfCaptureFailedEvent(Turf turf, Reason reason, @Nullable Integer challengerGangId) {
		this.turf             = turf;
		this.reason           = reason;
		this.challengerGangId = challengerGangId;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public enum Reason {
		/** The challenger left the turf and did not come back within Abandon_Grace_Seconds. */
		ABANDONED,
		/** An admin or an ownership change ended the contest. */
		CANCELLED,
		/** The defenders pushed the capture bar back to zero. */
		DEFENDED,
		/** The contest ran Max_Contest_Minutes without finishing; the defenders keep the turf. */
		STALLED
	}
}
