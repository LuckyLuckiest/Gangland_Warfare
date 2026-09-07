package org.luckyraven.gangland.copsncrooks.npc.police.config;

/**
 * Provides the cop count per wanted level, driven by either a formula or static parameters configured in
 * {@code settings.yml}.
 * <p>
 * Implementations live in {@code gangland-impl} and delegate to
 * {@code org.luckyraven.gangland.file.configuration.SettingAddon}, keeping {@code cops-n-crooks} fully decoupled from
 * the main plugin's file-loading infrastructure.
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

	double getMinSpawnDistance();

	double getMaxSpawnDistance();

	double getPhase1MinDistance();

	double getSpawnRadiusShrinkStep();

	int getVerticalSearchRange();

	int getSpawnYOffset();

	double getMaxSpawnYDiff();

	double getSpawnerMaxYDiff();

	int getMinOpenHorizontalSides();

	double getSpawnerPreferenceRadius();

	double getVisibilityCheckDistance();

	int getSpawnPhase1Attempts();

	int getSpawnPhase2Attempts();

	int getNavigationRecalculationTicks();

	int getStuckCheckIntervalTicks();

	int getMaxStuckChecks();

	int getMaxHopelessStuckChecks();

	double getHopelessCloseThreshold();

	double getMinProgressDistance();

	double getRangedMinDistance();

	double getRangedMaxDistance();

	int getMinRepathAfterLossTicks();

	/**
	 * Horizontal distance (blocks) from the target beyond which a pursuing cop gives up and returns.
	 */
	double getPursuitMaxDistance();

	/**
	 * Maximum AI ticks a cop spends in the PURSUING state before giving up and returning.
	 */
	int getPursuitMaxTicks();

	int getMaxReturnTicks();

	double getStationArrivalDistance();

	int getStartingAmmoMagazines();

	/**
	 * Radius (blocks) the GUARDING cop tries to stay within from the cuffed player awaiting jail transit.
	 */
	default double getGuardRadius() {
		return 5.0;
	}
}
