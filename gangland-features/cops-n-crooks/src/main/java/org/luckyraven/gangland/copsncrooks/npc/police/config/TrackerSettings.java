package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Tracker tags on getaway vehicles ({@code Cops.Tracker}). @since 0.20.0 */
public record TrackerSettings(boolean enabled, int minLevel, int tagSeconds, double tagRange, int pingSeconds, int expireSeconds) {

	/** Matches the 0.20.0 cops.yml defaults. */
	public static final TrackerSettings DEFAULT = new TrackerSettings(true, 2, 4, 48.0, 6, 150);

	public TrackerSettings {
		minLevel = Math.max(1, minLevel);
		tagSeconds = Math.max(0, tagSeconds);
		tagRange = Math.max(0.0, tagRange);
		pingSeconds = Math.max(0, pingSeconds);
		expireSeconds = Math.max(0, expireSeconds);
	}
}
