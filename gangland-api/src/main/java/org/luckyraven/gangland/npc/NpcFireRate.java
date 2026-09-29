package org.luckyraven.gangland.npc;

import org.luckyraven.keystone.npc.AbstractNpc;
import org.luckyraven.keystone.npc.spi.NpcRangedAttack;

/**
 * Turns a {@code Fire_Rate_Multiplier} into the Keystone fire-rate scale ({@link AbstractNpc#setFireRateScale}).
 * <p>
 * Keystone applies one scale to two clocks. An SPI (Bartizan) weapon gets {@code serverTicks / scale} weapon ticks,
 * so {@code 1 / multiplier} with the shipped {@code 1 / AI_Tick_Rate} gives 0.11's one weapon tick per AI tick. The
 * vanilla bow/crossbow fallback waits {@code 30 x scale} server ticks, but 0.11 waited 15 AI ticks
 * ({@code 15 x AI_Tick_Rate} server ticks), so that path gets half the scale to keep the same cadence.
 *
 * @since 0.12.0
 */
public final class NpcFireRate {

	private NpcFireRate() { }

	/** The fire-rate scale for {@code fireRateMultiplier} (above 0) and the NPC's ranged attack. */
	public static double scale(double fireRateMultiplier, NpcRangedAttack rangedAttack) {
		return (rangedAttack.isRanged() ? 1.0 : 0.5) / fireRateMultiplier;
	}
}
