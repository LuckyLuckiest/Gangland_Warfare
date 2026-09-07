package org.luckyraven.gangland.file.configuration.turf;

import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.luckyraven.gangland.copsncrooks.npc.civilian.npc.CivilianNpc;
import org.luckyraven.gangland.copsncrooks.npc.turf.TurfPowerupManager;
import org.luckyraven.gangland.copsncrooks.npc.turf.defender.TurfDefenderConfig;
import org.luckyraven.gangland.copsncrooks.npc.turf.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.turf.turfnpcs.TurfNpcContract;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bridges {@link TurfNpcContract} (turf-side surface) to the cops-n-crooks NPC managers. The turf module never imports
 * cops-n-crooks types directly; this impl is the only place that knows about both sides.
 *
 * <p>Defenders are spawned as {@link CivilianNpc} of the configured
 * type id (from {@code civilians.yml}) by {@link TurfDefenderDeployer}. The challenger-member set passed in is a
 * {@link Supplier} so a fresh {@link Gang#getMembers()} snapshot is taken on every targeting tick — players who join
 * the gang mid-contest are picked up automatically without redeploying.
 *
 * <p>The Quartermaster engage/disengage methods route through {@link TurfPowerupManager}, which holds each
 * Quartermaster's underlying CivilianNpc and flips its target + COMBAT state.
 */
@RequiredArgsConstructor
public final class TurfNpcContractImpl implements TurfNpcContract {

	private final TurfDefenderDeployer defenders;
	private final TurfDefenderConfig   defenderConfig;
	private final TurfPowerupManager   powerupNpcs;
	private final GangLookupContract   gangs;

	@Override
	public void deployDefenders(int turfId, Location spawnLocation, int challengerGangId, int count) {
		Supplier<Set<UUID>> attackers = () -> challengerMemberIds(challengerGangId);
		defenders.deploy(turfId, spawnLocation, defenderConfig.typeId(), attackers, count,
		                 defenderConfig.targetingRadius(), defenderConfig.lifespanSeconds());
	}

	@Override
	public void recallDefenders(int turfId) {
		defenders.recall(turfId);
	}

	@Override
	public void engageQuartermaster(int turfId, int challengerGangId) {
		powerupNpcs.engage(turfId, () -> challengerMemberIds(challengerGangId));
	}

	@Override
	public void disengageQuartermaster(int turfId) {
		powerupNpcs.disengage(turfId);
	}

	private Set<UUID> challengerMemberIds(int gangId) {
		Gang gang = gangs.findById(gangId);
		if (gang == null) return Set.of();
		Set<UUID> ids = new HashSet<>();
		for (Member member : gang.getMembers()) {
			ids.add(member.getUuid());
		}
		return ids;
	}
}
