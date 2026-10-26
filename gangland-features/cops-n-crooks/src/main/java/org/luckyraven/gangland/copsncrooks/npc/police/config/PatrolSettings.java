package org.luckyraven.gangland.copsncrooks.npc.police.config;

/**
 * Beat cops around stations ({@code Cops.Patrol}). {@code respawnSeconds} (0.21.1): how long a station waits after
 * losing a beat cop before it sends a replacement. @since 0.20.0
 */
public record PatrolSettings(boolean enabled, int copsPerStation, int activationRadius, int despawnRadius, int despawnAfterSeconds, int beatRadius, int pauseSecondsMin, int pauseSecondsMax, int tier, boolean reportWanted, int respawnSeconds) {

	/** {@code Respawn_Seconds} when the file has none. */
	public static final int DEFAULT_RESPAWN_SECONDS = 30;

	/** Matches the cops.yml defaults. */
	public static final PatrolSettings DEFAULT = new PatrolSettings(true, 2, 64, 96, 30, 24, 3, 6, 1, true);

	public PatrolSettings {
		copsPerStation = Math.max(0, copsPerStation);
		activationRadius = Math.max(1, activationRadius);
		despawnRadius = Math.max(1, despawnRadius);
		despawnAfterSeconds = Math.max(0, despawnAfterSeconds);
		beatRadius = Math.max(1, beatRadius);
		pauseSecondsMin = Math.max(0, pauseSecondsMin);
		pauseSecondsMax = Math.max(pauseSecondsMax, pauseSecondsMin);
		tier = Math.max(1, tier);
		respawnSeconds = Math.max(0, respawnSeconds);
	}

	/** The 0.20.0 shape, with the default {@link #DEFAULT_RESPAWN_SECONDS}. */
	public PatrolSettings(boolean enabled, int copsPerStation, int activationRadius, int despawnRadius,
	                      int despawnAfterSeconds, int beatRadius, int pauseSecondsMin, int pauseSecondsMax, int tier,
	                      boolean reportWanted) {
		this(enabled, copsPerStation, activationRadius, despawnRadius, despawnAfterSeconds, beatRadius, pauseSecondsMin,
		     pauseSecondsMax, tier, reportWanted, DEFAULT_RESPAWN_SECONDS);
	}
}
