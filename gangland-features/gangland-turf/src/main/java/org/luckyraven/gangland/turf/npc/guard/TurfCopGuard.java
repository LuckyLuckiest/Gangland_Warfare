package org.luckyraven.gangland.turf.npc.guard;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

// RED STUB (0.16.1 T-189): compiles against the final shape, behaviour intentionally absent.
public final class TurfCopGuard {

	private final Map<UUID, Object> engagements = new HashMap<>();

	public TurfCopGuard(JavaPlugin plugin,
	                    TurfManager turfs,
	                    UserLookupContract users,
	                    GangMembership membership,
	                    TurfPowerupManager powerups,
	                    TurfDefenderDeployer defenders,
	                    CopGuardConfig config) {
	}

	public void start() {
	}

	public void stop() {
		engagements.clear();
	}

	public void onCopHitPlayer(LivingEntity cop, Player victim) {
	}

	void tick() {
	}

	public static boolean protects(int victimGangId,
	                               int ownerGangId,
	                               boolean includeAllies,
	                               BiPredicate<Integer, Integer> allied) {
		return false;
	}
}
