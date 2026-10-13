package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Beat cops around stations ({@code Cops.Patrol}). @since 0.20.0 */
public record PatrolSettings(boolean enabled, int copsPerStation, int activationRadius, int despawnRadius, int despawnAfterSeconds, int beatRadius, int pauseSecondsMin, int pauseSecondsMax, int tier, boolean reportWanted) {

	/** TDD stub: neutral values until the green chunk sets the shipped defaults and clamps. */
	public static final PatrolSettings DEFAULT = new PatrolSettings(false, 0, 0, 0, 0, 0, 0, 0, 0, false);
}
