package me.luckyraven.turf.data;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.turf.state.TurfState;

/**
 * Live per-turf state that is <b>not</b> persisted. Capture progress resets to zero and state resets to IDLE on every
 * server start — in-flight captures do not survive restart. Cooldown anchors on {@link Turf#getLastCaptureTimestamp()}
 * which <i>is</i> persisted.
 */
@Getter
@Setter
public final class TurfRuntimeState {

	private final String    turfId;
	private       TurfState state;
	/**
	 * 0.0 .. 100.0
	 */
	private       double    captureProgress;
	/**
	 * null unless state == CONTESTING
	 */
	private       Integer   challengerGangId;
	/**
	 * epoch ms; used for abandon-grace detection
	 */
	private       long      lastChallengerSeenAt;

	public TurfRuntimeState(String turfId) {
		this.turfId          = turfId;
		this.state           = TurfState.IDLE;
		this.captureProgress = 0.0;
	}

	public void reset() {
		this.state                = TurfState.IDLE;
		this.captureProgress      = 0.0;
		this.challengerGangId     = null;
		this.lastChallengerSeenAt = 0L;
	}
}
