package org.luckyraven.gangland.civilians.npc;

import org.bukkit.entity.LivingEntity;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.Map;

/**
 * Resolves the shared faction squad a civilian should hunt {@code target} with (phase H12). Implemented by
 * {@link CivilianService} and injected into every {@link CivilianNpc} via setter, so combat behaviour that entered
 * combat without a hit (turf-defender retarget, idle re-engage) still shares one squad per faction and target instead
 * of hunting alone.
 *
 * @since 1.13.0
 */
@FunctionalInterface
public interface FactionSquads {

	NpcSquad squadFor(CivilianNpc npc, LivingEntity target);

	/**
	 * The scope this provider's squads live in (for example one turf). {@link CivilianService} recruits only NPCs of
	 * the same provider, and keeps squads of different scopes apart.
	 * A provider that keeps the default {@code ""} shares the street squad key, so a custom provider must return a
	 * non-empty scope.
	 */
	default String scope() {
		return "";
	}

	/**
	 * Extra shout placeholders for a squad this provider created, merged after {@code %faction%} (the provider wins on
	 * a clash). A turf provider supplies {@code gang} for {@code %gang%}.
	 */
	default Map<String, String> extras(NpcSquad squad) {
		return Map.of();
	}
}
