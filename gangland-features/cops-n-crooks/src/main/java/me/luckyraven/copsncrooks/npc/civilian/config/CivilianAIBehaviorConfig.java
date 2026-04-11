package me.luckyraven.copsncrooks.npc.civilian.config;

import me.luckyraven.copsncrooks.npc.NpcDifficulty;

/**
 * Per-type AI behavior configuration for a civilian NPC.
 * <p>
 * Technical navigation settings (recalculation ticks, stuck detection, etc.) are shared across all NPC types and live
 * in {@code settings.yml} under {@code NPC_Navigation}.
 *
 * @param wanderEnabled whether the NPC wanders when idle
 * @param wanderRange radius in blocks within which random wander targets are chosen
 * @param fleeEnabled whether the NPC flees when damaged
 * @param fleeRange how far (blocks) the NPC runs before stopping when fleeing
 * @param combatEnabled whether the NPC engages in combat (hostile types only)
 * @param attackDamage base damage dealt per attack
 * @param attackRange range in blocks within which a combat target is detected
 * @param attackIntervalTicks server ticks between attacks
 * @param difficulty difficulty profile that scales aim error, reaction time, fire rate, and melee damage
 */
public record CivilianAIBehaviorConfig(
		boolean wanderEnabled,
		int wanderRange,
		boolean fleeEnabled,
		int fleeRange,
		boolean combatEnabled,
		double attackDamage,
		double attackRange,
		int attackIntervalTicks,
		NpcDifficulty difficulty
) {
}
