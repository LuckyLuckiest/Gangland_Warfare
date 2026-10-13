package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Sirens you can hear ({@code Cops.Siren}). @since 0.20.0 */
public record SirenSettings(boolean enabled, int intervalSeconds, double minDistance, String sound, float volume, float pitch) {

	/** TDD stub: neutral values until the green chunk sets the shipped defaults and clamps. */
	public static final SirenSettings DEFAULT = new SirenSettings(false, 0, 0.0, "", 0f, 0f);
}
