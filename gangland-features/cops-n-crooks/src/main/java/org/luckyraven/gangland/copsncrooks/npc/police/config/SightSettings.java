package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Darkness and crowds hide you ({@code Cops.Sight}). @since 0.20.0 */
public record SightSettings(boolean enabled, int darkMaxLevel, double darkFactor, int dimMaxLevel, double dimFactor, double crowdFactor, int crowdMinCivilians, double crowdRadius, int crowdOutOfContactSeconds, double sneakFactor, double minFactor, int shotResetSeconds) {

	/** Matches the 0.20.0 cops.yml defaults. */
	public static final SightSettings DEFAULT = new SightSettings(true, 4, 0.4, 8, 0.7, 0.6, 3, 4.0, 2, 0.7, 0.25, 5);

	public SightSettings {
		darkMaxLevel = Math.max(0, darkMaxLevel);
		darkFactor = unit(darkFactor);
		dimMaxLevel = Math.max(0, dimMaxLevel);
		dimFactor = unit(dimFactor);
		crowdFactor = unit(crowdFactor);
		crowdMinCivilians = Math.max(0, crowdMinCivilians);
		crowdRadius = Math.max(0.0, crowdRadius);
		crowdOutOfContactSeconds = Math.max(0, crowdOutOfContactSeconds);
		sneakFactor = unit(sneakFactor);
		minFactor = unit(minFactor);
		shotResetSeconds = Math.max(0, shotResetSeconds);
	}

	private static double unit(double value) {
		return Math.min(1.0, Math.max(0.0, value));
	}
}
