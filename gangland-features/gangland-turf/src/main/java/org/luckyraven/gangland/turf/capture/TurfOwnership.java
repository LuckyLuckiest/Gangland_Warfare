package org.luckyraven.gangland.turf.capture;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;

import java.util.Objects;

/**
 * The one place that changes a turf's owning gang. Every owner writer (capture completion, admin setowner, delete,
 * gang disband, inactivity and orphan sweeps) goes through here, so a change always cancels a running contest first,
 * persists once and fires exactly one {@link TurfOwnerChangedEvent}. Main thread only; no locking.
 */
@CustomLog
public final class TurfOwnership {

	private final TurfManager turfs;

	public TurfOwnership(TurfManager turfs) {
		this.turfs = turfs;
	}

	/**
	 * Cancels an in-flight capture: if the turf is {@link TurfState#CONTESTING}, resets its runtime state and fires one
	 * {@link TurfCaptureFailedEvent}. Returns {@code true} when it cancelled.
	 */
	public boolean cancelContest(Turf turf, TurfCaptureFailedEvent.Reason reason) {
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		if (state == null || state.getState() != TurfState.CONTESTING) {
			return false;
		}
		Integer challenger = state.getChallengerGangId();
		state.reset();
		Bukkit.getPluginManager().callEvent(new TurfCaptureFailedEvent(turf, reason, challenger));
		return true;
	}

	/**
	 * Sets the owner. A non-capture cause cancels any running contest first. Writes and fires one
	 * {@link TurfOwnerChangedEvent} only when the owner actually changes. Returns {@code true} when it changed.
	 */
	public boolean setOwner(Turf turf, @Nullable Integer newOwnerGangId, TurfOwnerChangedEvent.Cause cause) {
		if (cause != TurfOwnerChangedEvent.Cause.CAPTURE) {
			cancelContest(turf, TurfCaptureFailedEvent.Reason.CANCELLED);
		}
		Integer oldOwnerGangId = turf.getOwnerGangId();
		if (Objects.equals(oldOwnerGangId, newOwnerGangId)) {
			return false;
		}
		turf.setOwnerGangId(newOwnerGangId);
		turfs.persist(turf);
		Bukkit.getPluginManager().callEvent(new TurfOwnerChangedEvent(turf, oldOwnerGangId, newOwnerGangId, cause));
		return true;
	}
}
