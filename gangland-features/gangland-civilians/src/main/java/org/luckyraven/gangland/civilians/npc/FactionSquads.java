package org.luckyraven.gangland.civilians.npc;

import org.bukkit.entity.LivingEntity;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.keystone.npc.NpcSquad;

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
}
