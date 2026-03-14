package me.luckyraven.copsncrooks.police.config;

import java.util.Map;

/**
 * Provides all cop-related configuration values.
 */
public interface CopConfigProvider {

	/**
	 * Returns the cop tier configuration for the given tier level.
	 *
	 * @param tier the tier number
	 *
	 * @return the tier config, or the highest available if tier exceeds max
	 */
	CopTierConfig getTierConfig(int tier);

	/**
	 * Returns the maximum configured tier number.
	 *
	 * @return the max tier
	 */
	int getMaxTier();

	/**
	 * Returns the number of cops to assign per wanted level.
	 *
	 * @return map of wanted level to cop count
	 */
	Map<Integer, Integer> getCopsPerWantedLevel();

	/**
	 * Returns the maximum number of cops allowed per player.
	 *
	 * @return the cap
	 */
	int getMaxCopsPerPlayer();

	/**
	 * Returns the tick rate at which AI should be evaluated.
	 *
	 * @return the AI tick rate
	 */
	int getAiTickRate();

	/**
	 * Returns the tick rate for spawn checks.
	 *
	 * @return the spawn check rate
	 */
	int getSpawnCheckRate();

	/**
	 * Returns the radius within which a cop can attempt to cuff a player.
	 *
	 * @return cuff radius in blocks
	 */
	double getCuffRadius();

	/**
	 * Returns the maximum number of cuff attempts before escalation.
	 *
	 * @return cuff attempt limit
	 */
	int getMaxCuffAttempts();

	/**
	 * Returns the cuffing cooldown in ticks.
	 *
	 * @return the cooldown duration in ticks
	 */
	int getCuffCooldownTicks();

	/**
	 * Returns the alert range in blocks.
	 *
	 * @return the range at which cops become alert
	 */
	double getAlertRange();

	/**
	 * Returns the combat engagement range in blocks.
	 *
	 * @return the combat range
	 */
	double getCombatRange();

	/**
	 * Returns the attack cooldown in ticks.
	 *
	 * @return cooldown ticks
	 */
	int getAttackCooldownTicks();
}