package org.luckyraven.gangland.copsncrooks.npc.police.config;

import java.util.Locale;
import java.util.Map;

/**
 * How far a shot is heard by cops ({@code cops.yml}'s {@code Cops.Shot_Noise} block).
 *
 * @param enabled {@code false} silences every weapon.
 * @param radius  noise radius in blocks by upper-case weapon type ({@code GUN}, {@code THROWABLE}, {@code MELEE}).
 * @since 0.15.0
 */
public record ShotNoiseSettings(boolean enabled, Map<String, Double> radius) {

	/** Matches the shipped cops.yml: GUN 48, THROWABLE 16, MELEE 0. */
	public static final ShotNoiseSettings DEFAULT = new ShotNoiseSettings(true,
			Map.of("GUN", 48.0, "THROWABLE", 16.0, "MELEE", 0.0));

	/** The radius for {@code weaponType}; 0 when disabled or the upper-cased type is unlisted. */
	public double radiusFor(String weaponType) {
		if (!enabled || weaponType == null) return 0.0;
		return radius.getOrDefault(weaponType.toUpperCase(Locale.ROOT), 0.0);
	}
}
