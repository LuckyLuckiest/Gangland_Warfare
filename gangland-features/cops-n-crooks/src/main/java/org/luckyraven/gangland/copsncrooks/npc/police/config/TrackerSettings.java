package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Tracker tags on getaway vehicles ({@code Cops.Tracker}). @since 0.20.0 */
public record TrackerSettings(boolean enabled, int minLevel, int tagSeconds, double tagRange, int pingSeconds, int expireSeconds) {

	/** TDD stub: neutral values until the green chunk sets the shipped defaults and clamps. */
	public static final TrackerSettings DEFAULT = new TrackerSettings(false, 0, 0, 0.0, 0, 0);
}
