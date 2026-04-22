package me.luckyraven.turf.data;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Persisted turf definition. Owner, income, bounds, and the cooldown anchor survive restart. Live state (current
 * capture progress, contesting gang) is tracked separately in {@link me.luckyraven.turf.data.TurfRuntimeState}.
 */
@Getter
public final class Turf {

	private final String       id;
	private final long         createdAt;
	@Setter
	private       String       displayName;
	@Setter
	private       CuboidRegion region;
	/**
	 * null = unclaimed
	 */
	@Setter
	private       Integer      ownerGangId;
	@Setter
	private       BigDecimal   incomeAmount;
	/**
	 * epoch ms of the last successful capture; 0 when never captured. Drives cooldown.
	 */
	@Setter
	private       long         lastCaptureTimestamp;

	public Turf(String id,
	            String displayName,
	            CuboidRegion region,
	            Integer ownerGangId,
	            BigDecimal incomeAmount,
	            long createdAt,
	            long lastCaptureTimestamp) {
		this.id                   = id;
		this.displayName          = displayName;
		this.region               = region;
		this.ownerGangId          = ownerGangId;
		this.incomeAmount         = incomeAmount;
		this.createdAt            = createdAt;
		this.lastCaptureTimestamp = lastCaptureTimestamp;
	}

	public boolean isUnclaimed() {
		return ownerGangId == null;
	}
}
