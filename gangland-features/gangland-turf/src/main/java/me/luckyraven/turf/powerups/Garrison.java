package me.luckyraven.turf.powerups;

import lombok.Getter;
import lombok.Setter;

/**
 * Pre-purchased defender stock for a single turf. Owner-keyed (one row per turf): when an enemy starts a capture, the
 * configured number of defenders is deployed and {@code count} is decremented by however many actually spawned.
 * Persisted so a server restart preserves whatever the owner paid for.
 */
@Getter
@Setter
public final class Garrison {

	private final int turfId;
	private       int count;

	public Garrison(int turfId, int count) {
		this.turfId = turfId;
		this.count  = count;
	}
}
