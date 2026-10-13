package org.luckyraven.gangland.copsncrooks.npc.police.config;

/**
 * The barricade standoff knobs ({@code cops.yml}'s {@code Cops.Standoff}).
 *
 * @since 0.21.0
 */
public record StandoffSettings(boolean enabled, int timerSeconds, int negotiateSeconds, int unreachableSeconds,
                               int manDownCount, int manDownWindowSeconds, double spotRadius, int postCount,
                               double postRadius, int starsOnTimeout, int cooldownSeconds) {

	/** TDD stub: neutral zeros; the green phase sets the shipped cops.yml numbers. */
	public static final StandoffSettings DEFAULT = new StandoffSettings(false, 0, 0, 0, 0, 0, 0.0, 0, 0.0, 0, 0);
}
