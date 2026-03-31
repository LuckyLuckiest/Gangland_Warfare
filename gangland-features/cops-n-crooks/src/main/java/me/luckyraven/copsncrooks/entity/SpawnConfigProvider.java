package me.luckyraven.copsncrooks.entity;

/**
 * Provides spawn-location configuration values shared by all {@link EntitySpawner} subtypes.
 */
public interface SpawnConfigProvider {

	/**
	 * Minimum distance from the target to spawn an NPC (blocks).
	 */
	double getMinSpawnDistance();

	/**
	 * Maximum distance from the target to spawn an NPC (blocks).
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
	 * Vertical range searched above/below the target Y level for valid ground (blocks).
	 */
	int getVerticalSearchRange();

	/**
	 * Y-offset applied to the target's Y when searching for spawn ground.
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
}
