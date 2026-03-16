package me.luckyraven.copsncrooks.police.config;

/**
 * Provides the cop count per wanted level, driven by either a formula or static parameters configured in
 * {@code settings.yml}.
 * <p>
 * Implementations live in {@code gangland-impl} and delegate to {@code me.luckyraven.file.configuration.SettingAddon},
 * keeping {@code cops-n-crooks} fully decoupled from the main plugin's file-loading infrastructure.
 */
public interface CopSettings {

	/**
	 * Returns how many cops should spawn for the given wanted level.
	 *
	 * @param level wanted level (1-based)
	 *
	 * @return cop count, always ≥ 1
	 */
	int getCountForLevel(int level);

	/**
	 * Returns the maximum wanted level. {@link YamlCopConfigProvider} generates one entry for every level from 1 to
	 * this value.
	 */
	int getMaxWantedLevel();

	// -------------------------------------------------------------------------
	// Core cop behaviour
	// -------------------------------------------------------------------------

	/**
	 * Hard cap on the number of cops allowed per wanted player.
	 */
	int getMaxCopsPerPlayer();

	/**
	 * Game ticks per AI evaluation cycle. Lower = more responsive, higher CPU cost.
	 */
	int getAiTickRate();

	/**
	 * Game ticks between spawn-check cycles.
	 */
	int getSpawnCheckRate();

	/**
	 * Default radius within which a cop can attempt to cuff a player (blocks).
	 */
	double getCuffRadius();

	/**
	 * Maximum failed cuff attempts before the cop escalates to combat.
	 */
	int getMaxCuffAttempts();

	/**
	 * Ticks between consecutive cuff attempts.
	 */
	int getCuffCooldownTicks();

	/**
	 * Distance at which an idle cop detects a wanted player and becomes alert (blocks).
	 */
	double getAlertRange();

	/**
	 * Base melee engagement range (blocks). Ranged attack range scales from this value.
	 */
	double getCombatRange();

	/**
	 * Ticks between melee attacks.
	 */
	int getAttackCooldownTicks();

	// -------------------------------------------------------------------------
	// Spawn settings
	// -------------------------------------------------------------------------

	double getMinSpawnDistance();

	double getMaxSpawnDistance();

	double getPhase1MinDistance();

	double getSpawnRadiusShrinkStep();

	int getVerticalSearchRange();

	int getSpawnYOffset();

	int getMinOpenHorizontalSides();

	double getSpawnerPreferenceRadius();

	double getVisibilityCheckDistance();

	int getSpawnPhase1Attempts();

	int getSpawnPhase2Attempts();

	// -------------------------------------------------------------------------
	// Navigation settings
	// -------------------------------------------------------------------------

	int getNavigationRecalculationTicks();

	int getStuckCheckIntervalTicks();

	int getMaxStuckChecks();

	int getMaxHopelessStuckChecks();

	double getHopelessCloseThreshold();

	double getMinProgressDistance();

	double getRangedMinDistance();

	double getRangedMaxDistance();

	// -------------------------------------------------------------------------
	// Return / despawn settings
	// -------------------------------------------------------------------------

	int getMaxReturnTicks();

	double getStationArrivalDistance();

	// -------------------------------------------------------------------------
	// Misc
	// -------------------------------------------------------------------------

	int getStartingAmmoMagazines();
}
