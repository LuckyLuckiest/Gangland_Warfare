package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** The "put that away" warning ({@code Cops.Warning}). @since 0.20.0 */
public record WarningSettings(boolean enabled, double range, int seconds, int repeatSeconds) {

	/** Matches the 0.20.0 cops.yml defaults. */
	public static final WarningSettings DEFAULT = new WarningSettings(true, 12.0, 5, 30);

	public WarningSettings {
		range = Math.max(0.0, range);
		seconds = Math.max(0, seconds);
		repeatSeconds = Math.max(0, repeatSeconds);
	}
}
