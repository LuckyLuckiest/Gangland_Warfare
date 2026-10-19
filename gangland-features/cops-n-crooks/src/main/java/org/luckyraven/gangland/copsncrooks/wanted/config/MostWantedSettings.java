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
		throw new UnsupportedOperationException("TDD stub");
	}
}
