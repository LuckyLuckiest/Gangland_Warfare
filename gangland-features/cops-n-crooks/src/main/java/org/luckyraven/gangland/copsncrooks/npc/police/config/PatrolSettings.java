package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Beat cops around stations ({@code Cops.Patrol}). @since 0.20.0 */
public record PatrolSettings(boolean enabled, int copsPerStation, int activationRadius, int despawnRadius, int despawnAfterSeconds, int beatRadius, int pauseSecondsMin, int pauseSecondsMax, int tier, boolean reportWanted) {

	/** Matches the 0.20.0 cops.yml defaults. */
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
	}
}
