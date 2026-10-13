package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Darkness and crowds hide you ({@code Cops.Sight}). @since 0.20.0 */
public record SightSettings(boolean enabled, int darkMaxLevel, double darkFactor, int dimMaxLevel, double dimFactor, double crowdFactor, int crowdMinCivilians, double crowdRadius, int crowdOutOfContactSeconds, double sneakFactor, double minFactor, int shotResetSeconds) {

	/** TDD stub: neutral values until the green chunk sets the shipped defaults and clamps. */
	public static final SightSettings DEFAULT = new SightSettings(false, 0, 0.0, 0, 0.0, 0.0, 0, 0.0, 0, 0.0, 0.0, 0);
}
