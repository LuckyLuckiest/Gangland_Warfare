package org.luckyraven.gangland.command.sub.module;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.module.ModuleDescriptor;
import org.luckyraven.keystone.module.artifact.ArtifactCoordinate;
import org.luckyraven.keystone.update.PluginVersion;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The decision logic behind {@code /glw module install}, kept free of Bukkit and of the network so it can be tested:
 * turning what the operator typed into a Maven coordinate, and judging a freshly downloaded jar's descriptor.
 */
final class ModuleInstalls {

	/**
	 * The six official module ids and the coordinate that ships each one. A code constant rather than config: the
	 * list only changes with a plugin release. A third-party module is installed by full {@code group:artifact}.
	 */
	static final Map<String, String> OFFICIAL = Map.of("mail", "org.luckyraven:gangland-mail",
	                                                   "turf", "org.luckyraven:gangland-turf",
	                                                   "civilians", "org.luckyraven:gangland-civilians",
	                                                   "copsncrooks", "org.luckyraven:cops-n-crooks",
	                                                   "gadget", "org.luckyraven:gangland-gadget",
	                                                   "npcshops", "org.luckyraven:gangland-npc-shops");

	private ModuleInstalls() {
	}

	/**
	 * Resolve what the operator typed: an official module id, or a {@code group:artifact[:version]} coordinate.
	 *
	 * @return the coordinate, or {@code null} when the input is neither.
	 */
	static @Nullable ArtifactCoordinate coordinate(@Nullable String input) {
		if (input == null || input.isBlank()) return null;

		String token    = input.trim();
		String official = OFFICIAL.get(token.toLowerCase(Locale.ROOT));

		if (official != null) return ArtifactCoordinate.parse(official);

		try {
			return ArtifactCoordinate.parse(token);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/**
	 * What a downloaded jar's descriptor says about this server. An incompatible jar is rejected outright, so its
	 * missing-requirement lists are empty; a compatible one is kept and every unmet requirement reported as a warning.
	 */
	record Verdict(boolean compatible, List<String> missingDepends, List<String> missingPlugins) {

	}

	static Verdict inspect(ModuleDescriptor descriptor, PluginVersion hostApi, Set<String> loadedModuleIds,
	                       Predicate<String> pluginEnabled) {
		if (!descriptor.isCompatibleWith(hostApi)) {
			return new Verdict(false, List.of(), List.of());
		}

		List<String> depends = descriptor.depends()
				.stream()
				.filter(id -> !loadedModuleIds.contains(id))
				.toList();
		List<String> plugins = descriptor.plugins()
				.stream()
				.filter(name -> !pluginEnabled.test(name))
				.toList();

		return new Verdict(true, depends, plugins);
	}

}
