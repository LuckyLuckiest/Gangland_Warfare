package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** The "put that away" warning ({@code Cops.Warning}). @since 0.20.0 */
public record WarningSettings(boolean enabled, double range, int seconds, int repeatSeconds) {

	/** TDD stub: neutral values until the green chunk sets the shipped defaults and clamps. */
	public static final WarningSettings DEFAULT = new WarningSettings(false, 0.0, 0, 0);
}
