package me.luckyraven.turf.powerups;

import lombok.Getter;
import lombok.Setter;

/**
 * Live "this buff is running on turf X until Y" record. Persisted so a server restart doesn't wipe a paid-for buff. The
 * catalogue counterpart is {@link PowerupDefinition} — this class only carries what the runtime needs: the originating
 * powerup id (for re-display in panels), the turf the buff is bound to, the resolved effect, and the absolute epoch-ms
 * expiry.
 *
 * <p>{@code id} is auto-allocated by the database (BIGINT auto-increment); a fresh in-memory buff carries
 * {@link #UNASSIGNED_ID} until the first save.
 */
@Getter
@Setter
public final class ActiveTurfBuff {

	public static final long UNASSIGNED_ID = -1L;

	private long       id;
	private int        turfId;
	private String     powerupId;
	private EffectType effectType;
	private double     magnitude;
	private long       expiresAt;

	public ActiveTurfBuff(long id, int turfId, String powerupId, EffectType effectType, double magnitude,
	                      long expiresAt) {
		this.id         = id;
		this.turfId     = turfId;
		this.powerupId  = powerupId;
		this.effectType = effectType;
		this.magnitude  = magnitude;
		this.expiresAt  = expiresAt;
	}

	public boolean isExpired(long now) {
		return now >= expiresAt;
	}

	public long remainingMillis(long now) {
		return Math.max(0L, expiresAt - now);
	}
}
