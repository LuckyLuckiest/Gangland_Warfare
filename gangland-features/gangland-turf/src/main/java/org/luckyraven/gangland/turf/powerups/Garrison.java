package org.luckyraven.gangland.turf.powerups;

import lombok.Getter;
import lombok.Setter;

/**
 * Pre-purchased defender stock for a single turf, one row per turf. {@code count} is guards owned, standing or in
 * reserve; only a contest kill, a purchase, the admin setter or an owner-change wipe changes it. Persisted so a server
 * restart preserves whatever the owner paid for.
 */
@Getter
@Setter
public final class Garrison {

	private final int turfId;
	private       int count;
	/** Hold order (W7-F): guards keep to their posts and the turf leaves cops alone. False = Defend. */
	private       boolean hold;

	public Garrison(int turfId, int count) {
		this.turfId = turfId;
		this.count  = count;
	}

	public Garrison(int turfId, int count, boolean hold) {
		this(turfId, count);
		this.hold = hold;
	}
}
