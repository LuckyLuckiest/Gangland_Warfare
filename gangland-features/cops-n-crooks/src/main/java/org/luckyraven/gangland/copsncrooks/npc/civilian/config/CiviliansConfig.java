package org.luckyraven.gangland.copsncrooks.npc.civilian.config;

import java.util.List;
import java.util.Map;

/**
 * Top-level configuration loaded from {@code civilians.yml}.
 *
 * @param defaultCivilianEntities vanilla entity types auto-classified as CIVILIAN
 * @param defaultPoliceEntities vanilla entity types auto-classified as POLICE
 * @param types map of type-id → {@link CivilianTypeConfig}
 * @param groups map of group-id → {@link CivilianGroupConfig}
 * @param aiEnabled whether civilian NPC AI ticking is active
 * @param aiTickRate game ticks between civilian AI evaluations
 */
public record CiviliansConfig(
		List<String> defaultCivilianEntities,
		List<String> defaultPoliceEntities,
		Map<String, CivilianTypeConfig> types,
		Map<String, CivilianGroupConfig> groups,
		boolean aiEnabled,
		int aiTickRate
) {
}
