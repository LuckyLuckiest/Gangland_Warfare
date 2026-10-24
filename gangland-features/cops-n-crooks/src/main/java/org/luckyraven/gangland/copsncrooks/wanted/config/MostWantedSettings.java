package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

/**
 * {@code Wanted.Most_Wanted} of {@code copsncrooks/wanted.yml}.
 *
 * @since 0.21.0
 */
public record MostWantedSettings(boolean enabled, int minStars, int holdSeconds, int cooldownSeconds) {

	/** The shipped {@code Wanted.Most_Wanted}. */
	public static final MostWantedSettings DEFAULT = new MostWantedSettings(true, 4, 10, 120);

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static MostWantedSettings parse(@Nullable NodeReader wanted) {
		if (wanted == null) {
			return DEFAULT;
		}

		NodeReader n = wanted.get("Most_Wanted").asMapping().reader();
		if (n == null) {
			return DEFAULT;
		}

		// IntAccess.min() rejects an out-of-range value back to the default; the spec clamps, so clamp here
		return new MostWantedSettings(n.get("Enable").asBool().orDefault(DEFAULT.enabled()),
		                              Math.max(1, n.get("Min_Stars").asInt().orDefault(DEFAULT.minStars())),
		                              Math.max(0, n.get("Hold_Seconds").asInt().orDefault(DEFAULT.holdSeconds())),
		                              Math.max(0, n.get("Cooldown_Seconds").asInt().orDefault(DEFAULT.cooldownSeconds())));
	}
}
