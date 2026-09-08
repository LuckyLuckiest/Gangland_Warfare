package org.luckyraven.gangland.civilians.npc.entity;

import org.bukkit.entity.EntityType;
import org.luckyraven.gangland.civilians.npc.config.CiviliansConfig;
import org.luckyraven.gangland.civilians.npc.config.CiviliansLoader;
import org.luckyraven.keystone.npc.spi.NpcMarkDefaults;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds the deleted {@code EntityMarkManager.getDefaultMarkForType}'s cascade (config police list → config
 * civilian list → hardcoded fallback) as the flat {@code Map<EntityType,String>} {@code NpcMarkManager} wants — the
 * insertion order below (fallback first, civilian list next, police list last) preserves the old code's priority:
 * a later {@code put} for the same {@link EntityType} wins, so a type explicitly listed as police always beats a
 * civilian-list or hardcoded default for that same type, exactly as the original "police checked first" order did.
 */
public class GanglandMarkDefaults implements NpcMarkDefaults {

	private final CiviliansLoader civiliansLoader;

	public GanglandMarkDefaults(CiviliansLoader civiliansLoader) {
		this.civiliansLoader = civiliansLoader;
	}

	@Override
	public Map<EntityType, String> defaults() {
		CiviliansConfig config = civiliansLoader.getLoadedConfig();

		Map<EntityType, String> defaults = new EnumMap<>(EntityType.class);

		// Hardcoded fallback (lowest priority) — EntityMarkManager.getDefaultMarkForType's default switch.
		defaults.put(EntityType.VILLAGER, EntityMark.CIVILIAN.name());
		defaults.put(EntityType.WANDERING_TRADER, EntityMark.CIVILIAN.name());
		defaults.put(EntityType.PLAYER, EntityMark.CIVILIAN.name());
		defaults.put(EntityType.PILLAGER, EntityMark.POLICE.name());

		// civilians.yml-configured civilian entity types (overrides the hardcoded fallback for the same type).
		processEntityTypes(config.defaultCivilianEntities()).forEach(type -> defaults.put(type, EntityMark.CIVILIAN.name()));

		// civilians.yml-configured police entity types — checked first by the deleted code, so applied last here.
		processEntityTypes(config.defaultPoliceEntities()).forEach(type -> defaults.put(type, EntityMark.POLICE.name()));

		return defaults;
	}

	private List<EntityType> processEntityTypes(List<String> entityTypes) {
		return entityTypes.stream().map(String::toUpperCase).map(EntityType::valueOf).toList();
	}

}
