package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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
		NodeReader block = wanted == null ? null : wanted.get("Getaway").asMapping().reader();
		if (block == null) return DEFAULT;

		List<Integer> experience = block.get("Experience").asList().ofInts().orEmpty().stream()
				.map(xp -> Math.max(0, xp))
				.toList();

		NodeReader.NodeAccess exclude = block.get("Exclude_Crimes");
		Set<String> excluded = exclude.node() == null ? DEFAULT.excludeCrimes() :
		                       exclude.asList().ofStrings().orEmpty().stream()
				                       .map(id -> id.trim().toLowerCase(Locale.ROOT))
				                       .collect(Collectors.toUnmodifiableSet());

		return new GetawaySettings(block.get("Enable").asBool().orDefault(DEFAULT.enabled()),
		                           experience.isEmpty() ? DEFAULT.experience() : experience,
		                           Math.max(0, block.get("Cooldown_Seconds").asInt()
		                                            .orDefault(DEFAULT.cooldownSeconds())),
		                           Math.max(0, block.get("Daily_Cap").asInt().orDefault(DEFAULT.dailyCap())),
		                           excluded);
	}

	/** XP for a getaway after {@code peak} earned stars: 0 below one star, the last entry above the list. */
	public int experienceFor(int peak) {
		if (peak < 1) return 0;

		return experience.get(Math.min(peak, experience.size()) - 1);
	}

	/** Whether a crime id (any case) is excluded from earning. */
	public boolean excludes(@Nullable String crimeId) {
		return crimeId != null && excludeCrimes.contains(crimeId.toLowerCase(Locale.ROOT));
	}
}
