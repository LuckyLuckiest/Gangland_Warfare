package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

/**
 * {@code Wanted.Trespass} of {@code copsncrooks/wanted.yml}: restricted regions commit {@code Trespass_Restricted}.
 *
 * @param enabled       {@code false} turns the trespass crime off.
 * @param repeatSeconds least seconds between two trespass crimes for one player and region.
 * @since 0.20.0
 */
public record TrespassSettings(boolean enabled, int repeatSeconds) {

	/** TDD stub: neutral until the green chunk sets the shipped default (enabled, 60 s). */
	public static final TrespassSettings DEFAULT = new TrespassSettings(false, 0);

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static TrespassSettings parse(@Nullable NodeReader wanted) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
