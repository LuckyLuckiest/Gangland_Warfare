package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.Set;

/**
 * {@code Wanted.Crew_Heat} of {@code copsncrooks/wanted.yml}.
 *
 * @since 0.21.0
 */
public record CrewHeatSettings(boolean enabled, double radius, Set<String> excludeCrimes) {

	// TDD stub: neutral values, the green step sets the shipped defaults
	public static final CrewHeatSettings DEFAULT = new CrewHeatSettings(false, 0.0, Set.of());

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static CrewHeatSettings parse(@Nullable NodeReader wanted) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
