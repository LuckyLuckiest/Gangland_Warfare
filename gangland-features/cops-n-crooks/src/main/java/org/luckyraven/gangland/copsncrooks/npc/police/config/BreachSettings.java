package org.luckyraven.gangland.copsncrooks.npc.police.config;

/**
 * The stack-up and breach knobs ({@code cops.yml}'s {@code Cops.Breach}, flashbang under {@code Cops.Breach.Flashbang}).
 *
 * @since 0.21.0
 */
public record BreachSettings(boolean enabled, int roofBlocks, int doorRadius, int stackSize, int stackWaitSeconds,
                             int entryGapTicks, int cooldownSeconds, int flashbangMinStars, double flashbangRadius,
                             int flashbangTicks) {

	/** TDD stub: neutral zeros; the green phase sets the shipped cops.yml numbers. */
	public static final BreachSettings DEFAULT = new BreachSettings(false, 0, 0, 0, 0, 0, 0, 0, 0.0, 0);
}
