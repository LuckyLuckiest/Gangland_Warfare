package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Marksman overwatch ({@code Cops.Overwatch}). @since 0.20.0 */
public record OverwatchSettings(boolean enabled, int minLevel, String role, double ringMin, double ringMax, int columns, int minHeight, double sightRange, int maxFailures, int reachSeconds, int maxSeconds) {

	/** TDD stub: neutral values until the green chunk sets the shipped defaults and clamps. */
	public static final OverwatchSettings DEFAULT = new OverwatchSettings(false, 0, "", 0.0, 0.0, 0, 0, 0.0, 0, 0, 0);
}
