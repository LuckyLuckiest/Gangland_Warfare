package org.luckyraven.gangland.copsncrooks.wanted.config;

/**
 * {@code Wanted.Bribe_Stars}: a pickup that bribes the cops into dropping stars.
 *
 * @param enabled        {@code false} spawns no bribe star.
 * @param stars          stars a pickup takes.
 * @param respawnSeconds seconds before a taken pickup returns.
 * @param pickupRadius   blocks within which a player takes it.
 * @param item           the item's XMaterial name.
 * @since 0.16.0
 */
public record BribeStarSettings(boolean enabled, int stars, int respawnSeconds, double pickupRadius, String item) {

	/** The shipped {@code Wanted.Bribe_Stars}. */
	public static final BribeStarSettings DEFAULT = new BribeStarSettings(true, 1, 300, 1.5, "NETHER_STAR");
}
