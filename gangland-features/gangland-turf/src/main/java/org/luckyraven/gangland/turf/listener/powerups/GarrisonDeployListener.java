package org.luckyraven.gangland.turf.listener.powerups;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureStartEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.defender.GarrisonRoster;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.turf.npc.defender.TurfGarrison;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pivots a turf's standing guards and its Quartermaster into a contest the moment an enemy starts capturing the turf,
 * and back when it ends. The guards are already there: the deployer stands them from the row's count, so nothing is
 * spent at contest start and nothing returns at the end. A completed capture stands the guards down as lost, and the
 * owner change that follows recalls them and resets the old owner's stock (T-270).
 */
@ListenerHandler
@RequiredArgsConstructor
public final class GarrisonDeployListener implements Listener {

	private final GarrisonManager      garrisons;
	private final TurfDefenderDeployer defenders;
	private final TurfPowerupManager   powerupNpcs;
	private final GangLookupContract   gangs;
	private final GarrisonRoster       roster;

	@EventHandler
	public void onCaptureStart(TurfCaptureStartEvent event) {
		Turf turf = event.getTurf();
		// Only owned-turf captures arm the garrison + Quartermaster — unclaimed turfs by definition have no owner
		// to buy stock for and no Quartermaster to defend them, so neither hook applies.
		if (turf.isUnclaimed()) return;

		int challengerGangId = event.getChallengerGang().getId();

		// Engage the Quartermaster regardless of garrison stock — even with no garrison, the Quartermaster itself
		// fights back so an attacker can never just walk in and stand on a defenceless turf.
		powerupNpcs.engage(turf.getId(), () -> challengerMemberIds(challengerGangId));

		// A group exists for the whole contest even at stock 0, so a guard bought mid-contest fights and pays a bounty.
		TurfGarrison garrison = roster.garrisonOf(turf.getId());
		if (garrison != null) {
			defenders.startGarrison(garrison, () -> challengerMemberIds(challengerGangId));
		}
	}

	@EventHandler
	public void onCaptured(TurfCapturedEvent event) {
		// the owner change fired first, so on a normal capture the group is already gone and this is a no-op
		defenders.standDown(event.getTurf().getId(), false);
		powerupNpcs.disengage(event.getTurf().getId());
	}

	/**
	 * T-270: garrison stock belongs to the gang that bought it. A turf that changes hands for any reason (capture, admin,
	 * disband, inactivity) recalls the old owner's guards and starts with no stock.
	 */
	@EventHandler
	public void onOwnerChanged(TurfOwnerChangedEvent event) {
		int turfId = event.getTurf().getId();
		defenders.recall(turfId);
		garrisons.remove(turfId);
	}

	/** A failed contest ends the fight: the guards stand down, walk home and stay; the stock does not change. */
	@EventHandler
	public void onFailed(TurfCaptureFailedEvent event) {
		Turf turf = event.getTurf();
		defenders.standDown(turf.getId(), true);
		powerupNpcs.disengage(turf.getId());
	}

	/**
	 * A fresh {@link Gang#getMembers()} snapshot per call (not cached) so a player who joins the challenger gang
	 * mid-contest is picked up on the next targeting tick without redeploying — same contract the deleted
	 * {@code TurfNpcContractImpl} gave both {@code engage}/{@code deploy}'s supplier arguments.
	 */
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
