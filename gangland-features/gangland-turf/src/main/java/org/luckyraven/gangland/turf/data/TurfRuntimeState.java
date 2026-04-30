package org.luckyraven.gangland.turf.data;

import lombok.Getter;
import lombok.Setter;
import org.luckyraven.gangland.turf.state.CapturePhase;
import org.luckyraven.gangland.turf.state.TurfState;

/**
 * Live per-turf state that is <b>not</b> persisted. Capture progress resets to zero and state resets to IDLE on every
 * server start — in-flight captures do not survive restart. Cooldown anchors on {@link Turf#getLastCaptureTimestamp()}
 * which <i>is</i> persisted.
 */
@Getter
@Setter
public final class TurfRuntimeState {

	private final int          turfId;
	private       TurfState    state;
	/**
	 * Unclaimed-turf sub-state: which half of the two-phase capture is currently in play. Ignored for owned-turf
	 * captures (they're single-phase).
	 */
	private       CapturePhase phase;
	/**
	 * 0.0 .. 100.0 — progress <i>within the current phase</i>. Crossing 100 in {@link CapturePhase#CLAIM} transitions
	 * to {@link CapturePhase#CONSOLIDATE} at progress=0; crossing 0 in CONSOLIDATE reverts to CLAIM at progress=100.
	 */
	private       double       captureProgress;
	/**
	 * null unless state == CONTESTING
	 */
	private       Integer      challengerGangId;
	/**
	 * epoch ms; used for abandon-grace detection
	 */
	private       long         lastChallengerSeenAt;

	public TurfRuntimeState(int turfId) {
		this.turfId          = turfId;
		this.state           = TurfState.IDLE;
		this.phase           = CapturePhase.CLAIM;
		this.captureProgress = 0.0;
	}

	public void reset() {
		this.state                = TurfState.IDLE;
		this.phase                = CapturePhase.CLAIM;
		this.captureProgress      = 0.0;
		this.challengerGangId     = null;
		this.lastChallengerSeenAt = 0L;
	}
}
