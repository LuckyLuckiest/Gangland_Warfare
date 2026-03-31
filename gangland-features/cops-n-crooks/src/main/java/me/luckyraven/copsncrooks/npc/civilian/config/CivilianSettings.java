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
}
