package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.List;

/**
 * {@code Wanted.Witnesses} of {@code copsncrooks/wanted.yml}: which crimes wait for a witness when no cop saw them.
 *
 * @param enabled {@code false} counts every crime at once (shipped 0.15 behaviour).
 * @param crimes  crime ids gated on a witness.
 * @since 0.20.0
 */
public record WitnessSettings(boolean enabled, List<String> crimes) {

	/** The shipped {@code Wanted.Witnesses}: enabled, gating Car_Theft and Assault_Civilian. */
	public static final WitnessSettings DEFAULT = new WitnessSettings(true, List.of("Car_Theft", "Assault_Civilian"));

	public WitnessSettings {
		crimes = crimes == null ? List.of() : List.copyOf(crimes);
	}

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static WitnessSettings parse(@Nullable NodeReader wanted) {
		if (wanted == null) return DEFAULT;

		NodeReader block = block(wanted, "Witnesses");
		if (block == null) return DEFAULT;

		List<String> crimes = block.get("Crimes").asList().ofStrings().orNull();

		return new WitnessSettings(block.get("Enable").asBool().orDefault(DEFAULT.enabled()),
		                           crimes == null ? DEFAULT.crimes() : crimes);
	}

	private static @Nullable NodeReader block(NodeReader parent, String key) {
		return parent.get(key).asMapping().reader();
	}
}
