package org.luckyraven.gangland.turf.npc.defender;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.config.TurfNpcsConfigLoader;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;

import java.util.List;

/** Assembles a turf's garrison and its live guard numbers; the bodies arrive in task G. Stub until then. */
public final class GarrisonRoster {

	private final TurfNpcsConfigLoader npcConfig;

	public GarrisonRoster(TurfManager turfs, GarrisonManager garrisons, TurfNpcsConfigLoader npcConfig,
	                      GangLookupContract gangs, TurfMessages text, PowerupRegistry registry,
	                      ActiveBuffManager buffs) {
		this.npcConfig = npcConfig;
	}

	public List<Integer> stockedTurfIds() {
		return List.of();
	}

	@Nullable
	public TurfGarrison garrisonOf(int turfId) {
		return null;
	}

	public TurfDefenderConfig configOf(int turfId) {
		return npcConfig.getDefenderConfig();
	}

	public double guardHealth(int turfId) {
		return 1.0;
	}
}
