package org.luckyraven.gangland.copsncrooks.npc.police.config;

/**
 * The stack-up and breach knobs ({@code cops.yml}'s {@code Cops.Breach}, flashbang under {@code Cops.Breach.Flashbang}).
 *
 * @since 0.21.0
 */
public record BreachSettings(boolean enabled, int roofBlocks, int doorRadius, int stackSize, int stackWaitSeconds,
                             int entryGapTicks, int cooldownSeconds, int flashbangMinStars, double flashbangRadius,
                             int flashbangTicks) {

	public static final BreachSettings DEFAULT = new BreachSettings(true, 2, 6, 3, 6, 20, 30, 4, 8.0, 40);
}
