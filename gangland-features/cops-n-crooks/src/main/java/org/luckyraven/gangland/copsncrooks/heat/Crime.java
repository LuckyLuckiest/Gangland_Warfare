package org.luckyraven.gangland.copsncrooks.heat;

import lombok.Getter;

/**
 * The crimes the 0.12 heat ledger scores. Each crime has a heat weight read from {@code cops.yml}
 * ({@code Heat.Crimes.<config key>}), falling back to {@link #getDefaultWeight()} when the key is absent.
 *
 * <p>{@link #isKill()} marks the crimes the turf-war multiplier applies to (a kill whose victim stands inside a
 * contested turf).
 */
@Getter
public enum Crime {

	KILL_PLAYER("Kill_Player", 80, true),
	KILL_CIVILIAN("Kill_Civilian", 100, true),
	KILL_COP("Kill_Cop", 150, true),
	ASSAULT_COP("Assault_Cop", 100, false);

	private final String  configKey;
	private final int     defaultWeight;
	private final boolean kill;

	Crime(String configKey, int defaultWeight, boolean kill) {
		this.configKey     = configKey;
		this.defaultWeight = defaultWeight;
		this.kill          = kill;
	}

}
