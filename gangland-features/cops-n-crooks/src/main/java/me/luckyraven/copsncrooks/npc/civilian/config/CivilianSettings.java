package me.luckyraven.copsncrooks.npc.civilian.config;

import me.luckyraven.copsncrooks.entity.npc.NpcNavigationConfig;

/**
 * Contract for civilian-specific runtime settings.
 * <p>
 * Implemented in {@code gangland-impl} so that the {@code cops-n-crooks} feature module never imports {@code Settings}
 * directly.
 */
public interface CivilianSettings extends NpcNavigationConfig {

	/**
	 * Returns whether civilian AI ticking is enabled.
	 */
	boolean isCivilianAiEnabled();

	/**
	 * Returns the number of game ticks between civilian AI evaluations.
	 */
	int getCivilianAiTickRate();

	// ── Spawner proximity ─────────────────────────────────────────────────────

	/**
	 * Radius (blocks) within which a player activates a civilian spawner.
	 */
	double getCivilianSpawnerActivationRadius();

	/**
	 * Radius (blocks) beyond which all civilians from a spawner are despawned when no player is within it.
	 */
	double getCivilianSpawnerDespawnRadius();

	/**
	 * Maximum number of civilians that can be alive at once per spawner.
	 */
	int getCivilianSpawnerMaxNpcs();

	/**
	 * Ticks between each spawner proximity check cycle.
	 */
	int getCivilianSpawnerCheckInterval();

	/**
	 * Default civilian type ID to use when a spawner has no pinned type. Must match a type key defined in
	 * civilians.yml. Empty string disables auto-spawn for untyped spawners.
	 */
	String getCivilianSpawnerDefaultTypeId();
}
