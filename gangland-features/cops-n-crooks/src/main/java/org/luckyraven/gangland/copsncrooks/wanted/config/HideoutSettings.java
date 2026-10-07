package org.luckyraven.gangland.copsncrooks.wanted.config;

/**
 * {@code Wanted.Evasion.Hideout}: the search timer's speed while the player is inside a hideout.
 *
 * @param enabled {@code false} makes a hideout no different from open ground.
 * @param speed   timer speed inside a hideout.
 * @since 0.16.0
 */
public record HideoutSettings(boolean enabled, double speed) {

	/** The shipped {@code Wanted.Evasion.Hideout}. */
	public static final HideoutSettings DEFAULT = new HideoutSettings(true, 2.0);
}
