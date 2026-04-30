package org.luckyraven.gangland.copsncrooks.npc.entity;

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
	 * Maximum |spawnY - targetY| allowed for a chosen spawn location (blocks). The vertical search may scan further to
	 * find ground, but the resulting position is rejected if the player and the spawn point are not on a similar Y
	 * level — keeps NPCs from materialising on rooftops or in basements relative to the player.
	 */
	double getMaxSpawnYDiff();

	/**
	 * Maximum |spawnerY - targetY| allowed when picking a registered spawner (blocks). Filters out spawners whose Y is
	 * too far from the target so the spawned NPC can plausibly path to them.
	 */
	double getSpawnerMaxYDiff();

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
