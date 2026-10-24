package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * {@code Wanted.Crew_Heat} of {@code copsncrooks/wanted.yml}.
 *
 * @since 0.21.0
 */
public record CrewHeatSettings(boolean enabled, double radius, Set<String> excludeCrimes) {

	/**
	 * The shipped {@code Wanted.Crew_Heat}. Exclude_Crimes is council ruling C-12: the three 0.20.0 crimes that make no
	 * sense to share heat over, lower-cased.
	 */
	public static final CrewHeatSettings DEFAULT = new CrewHeatSettings(true, 16.0,
	                                                                    Set.of("trespass_restricted", "evading_police",
	                                                                           "brandish_near_cop"));

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static CrewHeatSettings parse(@Nullable NodeReader wanted) {
		if (wanted == null) {
			return DEFAULT;
		}
		NodeReader heat = block(wanted, "Crew_Heat");
		if (heat == null) {
			return DEFAULT;
		}

		CrewHeatSettings d        = DEFAULT;
		Set<String>      excludes = d.excludeCrimes();

		NodeReader.NodeAccess access = heat.get("Exclude_Crimes");
		if (access.node() != null) {
			Set<String> out = new LinkedHashSet<>();
			for (String entry : access.asList().ofStrings().orEmpty()) {
				if (entry == null || entry.isBlank()) {
					continue;
				}
				out.add(entry.strip().toLowerCase(Locale.ROOT));
			}
			excludes = Set.copyOf(out);
		}

		return new CrewHeatSettings(
				heat.get("Enable").asBool().orDefault(d.enabled()),
				Math.max(0, heat.get("Radius").asDouble().orDefault(d.radius())),
				excludes);
	}

	private static @Nullable NodeReader block(NodeReader parent, String key) {
		return parent.get(key).asMapping().reader();
	}
}
