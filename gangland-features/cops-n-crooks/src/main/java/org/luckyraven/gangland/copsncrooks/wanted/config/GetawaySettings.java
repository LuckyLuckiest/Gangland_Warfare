package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code Wanted.Getaway} of {@code copsncrooks/wanted.yml}. {@code excludeCrimes} is shared with Most Wanted
 * (council C-37): crime ids whose stars never count as earned.
 *
 * @since 0.21.0
 */
public record GetawaySettings(boolean enabled, List<Integer> experience, int cooldownSeconds, int dailyCap,
                              Set<String> excludeCrimes) {

	/** The shipped {@code Wanted.Getaway}. */
	public static final GetawaySettings DEFAULT = new GetawaySettings(true, List.of(0, 25, 75, 175, 350), 600, 1000,
	                                                                  Set.of("trespass_restricted", "evading_police",
	                                                                         "brandish_near_cop"));

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static GetawaySettings parse(@Nullable NodeReader wanted) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** XP for a getaway after {@code peak} earned stars: 0 below one star, the last entry above the list. */
	public int experienceFor(int peak) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Whether a crime id (any case) is excluded from earning. */
	public boolean excludes(@Nullable String crimeId) {
		return crimeId != null && excludeCrimes.contains(crimeId.toLowerCase(Locale.ROOT));
	}
}
