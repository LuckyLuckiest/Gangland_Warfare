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

	/** TDD stub: neutral until the green chunk sets the shipped default (enabled, Car_Theft + Assault_Civilian). */
	public static final WitnessSettings DEFAULT = new WitnessSettings(false, List.of());

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static WitnessSettings parse(@Nullable NodeReader wanted) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
