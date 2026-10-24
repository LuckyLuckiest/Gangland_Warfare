package org.luckyraven.gangland.copsncrooks.npc.police.config;

/**
 * The barricade standoff knobs ({@code cops.yml}'s {@code Cops.Standoff}).
 *
 * @since 0.21.0
 */
public record StandoffSettings(boolean enabled, int timerSeconds, int negotiateSeconds, int unreachableSeconds,
                               int manDownCount, int manDownWindowSeconds, double spotRadius, int postCount,
                               double postRadius, int starsOnTimeout, int cooldownSeconds) {

	public static final StandoffSettings DEFAULT = new StandoffSettings(true, 45, 15, 10, 2, 30, 8.0, 4, 12.0, 1, 60);
}
