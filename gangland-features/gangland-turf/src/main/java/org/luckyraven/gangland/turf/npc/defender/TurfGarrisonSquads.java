package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.entity.LivingEntity;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.FactionSquads;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.Map;

/**
 * One per contested turf: the guards of a turf hunt in a squad scoped to that turf, so two attacks on two turfs of the
 * same gang never share one, and their shouts carry the gang tag as {@code %gang%}.
 */
public final class TurfGarrisonSquads implements FactionSquads {

	private final CivilianService civilianService;
	private final int             turfId;
	private final String          tag;

	public TurfGarrisonSquads(CivilianService civilianService, int turfId, String tag) {
		this.civilianService = civilianService;
		this.turfId          = turfId;
		this.tag             = tag;
	}

	@Override
	public String scope() {
		return "turf:" + turfId;
	}

	@Override
	public NpcSquad squadFor(CivilianNpc npc, LivingEntity target) {
		return civilianService.squadFor(npc, target, scope());
	}

	@Override
	public Map<String, String> extras(NpcSquad squad) {
		return Map.of("gang", tag);
	}
}
