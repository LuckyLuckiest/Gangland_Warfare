package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Marksman overwatch ({@code Cops.Overwatch}). @since 0.20.0 */
public record OverwatchSettings(boolean enabled, int minLevel, String role, double ringMin, double ringMax, int columns, int minHeight, double sightRange, int maxFailures, int reachSeconds, int maxSeconds) {

	/** Matches the 0.20.0 cops.yml defaults. */
	public static final OverwatchSettings DEFAULT = new OverwatchSettings(true, 4, "Marksman", 10.0, 20.0, 8, 4, 48.0, 3, 20, 120);

	public OverwatchSettings {
		minLevel = Math.max(1, minLevel);
		role = role == null ? "Marksman" : role;
		columns = Math.max(1, columns);
		minHeight = Math.max(0, minHeight);
		ringMax = Math.max(ringMax, ringMin);
		sightRange = Math.max(0.0, sightRange);
		maxFailures = Math.max(1, maxFailures);
		reachSeconds = Math.max(0, reachSeconds);
		maxSeconds = Math.max(0, maxSeconds);
	}
}
