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

	/**
	 * Minimum distance from the player to spawn a cop (blocks).
	 */
	double getMinSpawnDistance();

	/**
	 * Maximum distance from the player to spawn a cop (blocks).
	 */
	double getMaxSpawnDistance();

	/**
	 * Minimum distance used during phase-1 (preferred-ring) spawn attempts (blocks).
	 */
	double getPhase1MinDistance();

	/**
	 * Distance the spawn ring radius shrinks per phase-2 iteration (blocks).
	 */
	double getSpawnRadiusShrinkStep();

	/**
	 * Vertical range searched above/below the player's Y level for valid ground (blocks).
	 */
	int getVerticalSearchRange();

	/**
	 * Y-offset applied to the player's Y when searching for spawn ground.
	 */
	int getSpawnYOffset();

	/**
	 * Minimum number of open horizontal sides required at a spawn position.
	 */
	int getMinOpenHorizontalSides();

	/**
	 * Maximum distance within which a registered spawner is preferred over a random position (blocks).
	 */
	double getSpawnerPreferenceRadius();

	/**
	 * Distance within which another player triggers the visibility check during despawn (blocks).
	 */
	double getVisibilityCheckDistance();

	/**
	 * Number of attempts in phase-1 (preferred ring, behind-player) of spawn location selection.
	 */
	int getSpawnPhase1Attempts();

	/**
	 * Number of attempts per shrink step in phase-2 of spawn location selection.
	 */
	int getSpawnPhase2Attempts();

	/**
	 * Ticks between navigation path recalculations.
	 */
	int getNavigationRecalculationTicks();

	/**
	 * AI ticks between movement-progress samples for stuck detection.
	 */
	int getStuckCheckIntervalTicks();

	/**
	 * Consecutive stuck samples before navigation is considered stuck.
	 */
	int getMaxStuckChecks();

	/**
	 * Consecutive stuck samples before navigation is considered permanently hopeless.
	 */
	int getMaxHopelessStuckChecks();

	/**
	 * Distance threshold below which a hopeless cop can still reach the target directly (blocks).
	 */
	double getHopelessCloseThreshold();

	/**
	 * Minimum distance the NPC must travel between samples to count as progress (blocks).
	 */
	double getMinProgressDistance();

	/**
	 * Minimum distance from the target for a ranged cop to hold position (blocks).
	 */
	double getRangedMinDistance();

	/**
	 * Maximum distance from the target for a ranged cop to hold position (blocks).
	 */
	double getRangedMaxDistance();

	/**
	 * Minimum number of AI ticks that must have elapsed from the last path request before a path-loss recovery re-path
	 * is allowed. Guards against requesting a new path before Citizens has finished computing the previous one
	 * (Citizens takes 1–2 ticks to start navigating after {@code setTarget} is called).
	 */
	double getMinRepathAfterLossTicks();

	/**
	 * Maximum AI ticks a cop waits at the station before being force-despawned.
	 */
	int getMaxReturnTicks();

	/**
	 * Distance to a station at which the cop considers itself arrived and attempts despawn (blocks).
	 */
	double getStationArrivalDistance();

	/**
	 * Number of full magazine reloads worth of ammo given to a cop NPC on spawn.
	 */
	int getStartingAmmoMagazines();
}