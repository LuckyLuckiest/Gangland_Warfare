package org.luckyraven.gangland.turf.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.state.CapturePhase;

/**
 * Fired only when capture progress <b>crosses upward</b> through a configured milestone (default 25 / 50 / 75).
 * Down-crossings are not emitted, so a see-saw fight does not flood listeners.
 *
 * <p>{@link #getProgress()} keeps its legacy meaning — progress within the current phase, 0..100. For listeners
 * that need to render both halves of an unclaimed two-phase capture (e.g. the dual bossbar UI) the event also carries
 * the active {@link #getPhase() phase} plus split {@link #getClaimProgress() claim} /
 * {@link #getConsolidateProgress() consolidate} progress so they don't have to re-read live runtime state racily
 * off-thread.
 */
@Getter
public final class TurfCaptureProgressEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Turf         turf;
	private final double       progress;
	private final CapturePhase phase;
	private final double       claimProgress;
	private final double       consolidateProgress;

	public TurfCaptureProgressEvent(Turf turf,
	                                double progress,
	                                CapturePhase phase,
	                                double claimProgress,
	                                double consolidateProgress) {
		this.turf                = turf;
		this.progress            = progress;
		this.phase               = phase;
		this.claimProgress       = claimProgress;
		this.consolidateProgress = consolidateProgress;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
