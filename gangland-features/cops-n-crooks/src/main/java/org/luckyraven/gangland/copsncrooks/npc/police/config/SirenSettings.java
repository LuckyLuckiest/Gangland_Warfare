package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Sirens you can hear ({@code Cops.Siren}). @since 0.20.0 */
public record SirenSettings(boolean enabled, int intervalSeconds, double minDistance, String sound, float volume, float pitch) {

	/** Matches the 0.20.0 cops.yml defaults. */
	public static final SirenSettings DEFAULT = new SirenSettings(true, 5, 24.0, "BLOCK_NOTE_BLOCK_BELL", 3.0f, 1.0f);

	public SirenSettings {
		intervalSeconds = Math.max(1, intervalSeconds);
		minDistance = Math.max(0.0, minDistance);
		sound = sound == null ? "" : sound;
		volume = Math.max(0f, volume);
		pitch = Math.max(0.5f, Math.min(2.0f, pitch));
	}
}
