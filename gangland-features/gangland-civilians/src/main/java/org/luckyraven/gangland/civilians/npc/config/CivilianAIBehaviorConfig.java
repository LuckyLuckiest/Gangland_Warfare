package org.luckyraven.gangland.civilians.npc.config;

import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcMeleeProfile;

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
 * @param attackRange range in blocks within which a combat target is detected (melee engage distance; ranged types
 * 		fire at anything they see within {@code alertRange})
 * @param attackIntervalTicks server ticks between attacks, and this type's melee cooldown
 * @param difficulty difficulty profile that scales aim error, reaction time, fire rate, and melee damage
 * @param alertRange sight range in blocks, and how far a faction member being hit is heard ({@code Combat.Alert_Range})
 * @param searchSeconds seconds the NPC's squad may go without seeing its target before it gives up
 * 		({@code Combat.Search_Seconds})
 * @param tactics squad-tactics tuning ({@code Combat.Tactics}): formation arc and engagement.
 * @param melee melee tuning ({@code Combat.Melee}): reach, approach, damage falloff. {@code cooldownTicks} is always
 * 		{@code attackIntervalTicks}, not a separate key.
 * @param fireRateMultiplier gun cadence as a fraction of the weapon's own (player) fire rate
 * 		({@code Combat.Fire_Rate_Multiplier}); the NPC's Keystone fire-rate scale is its inverse.
 * @param retreat when a badly hurt member breaks off to cover ({@code Combat.Retreat}).
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
		NpcDifficulty difficulty,
		double alertRange,
		int searchSeconds,
		TacticsConfig tactics,
		NpcMeleeProfile melee,
		double fireRateMultiplier,
		RetreatSettings retreat
) {

	/** Legacy 11-argument constructor, kept for existing callers: default tactics, a melee profile built from the
	 *  attack interval and the documented civilian defaults (reach 3.0, approach 2.0, spread 0.15, edge 0.7), fire-rate
	 *  multiplier 1.0 and {@link RetreatSettings#DEFAULT}. */
	public CivilianAIBehaviorConfig(boolean wanderEnabled, int wanderRange, boolean fleeEnabled, int fleeRange,
	                                boolean combatEnabled, double attackDamage, double attackRange,
	                                int attackIntervalTicks, NpcDifficulty difficulty, double alertRange,
	                                int searchSeconds) {
		this(wanderEnabled, wanderRange, fleeEnabled, fleeRange, combatEnabled, attackDamage, attackRange,
		     attackIntervalTicks, difficulty, alertRange, searchSeconds, TacticsConfig.DEFAULT,
		     new NpcMeleeProfile(3.0, 2.0, Math.max(1, attackIntervalTicks), 0.15, 0.7), 1.0, RetreatSettings.DEFAULT);
	}
}
