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

	/** The shipped {@code Wanted.Trespass}: enabled, one crime per region every 60 s. */
	public static final TrespassSettings DEFAULT = new TrespassSettings(true, 60);

	public TrespassSettings {
		repeatSeconds = Math.max(0, repeatSeconds);
	}

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static TrespassSettings parse(@Nullable NodeReader wanted) {
		if (wanted == null) return DEFAULT;

		NodeReader block = block(wanted, "Trespass");
		if (block == null) return DEFAULT;

		int repeat = block.get("Repeat_Seconds").asInt().orDefault(DEFAULT.repeatSeconds());
		// ponytail: second read only reports a negative value as config.range; the record clamps it to 0
		block.get("Repeat_Seconds").asInt().min(0).orDefault(0);
		return new TrespassSettings(block.get("Enable").asBool().orDefault(DEFAULT.enabled()), repeat);
	}

	private static @Nullable NodeReader block(NodeReader parent, String key) {
		return parent.get(key).asMapping().reader();
	}
}
