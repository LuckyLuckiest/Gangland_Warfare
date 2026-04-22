package me.luckyraven.turf.state;

/**
 * Runtime state of a turf. Persisted state only carries ownerGangId and lastCaptureTimestamp; the enum below is
 * in-memory and resets to IDLE on server start.
 */
public enum TurfState {

	IDLE,
	CONTESTING,
	COOLDOWN
}
