package org.luckyraven.gangland.turf.listener.powerups;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureStartEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderConfig;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Auto-deploys the owning gang's pre-purchased defender garrison the moment an enemy starts capturing the turf, and
 * pivots the per-turf Quartermaster NPC into hostile mode for the duration of the contest. The garrison is consumed (so
 * a turf with stock=3 spawns 3 defenders, then drops back to stock=0 — the owner has to re-buy from the Quartermaster).
 * Defenders are recalled and the Quartermaster is pacified on capture-complete or capture-failed regardless of who won,
 * so a finished contest never leaves stragglers behind.
 *
 * <p>Spawn anchor is the turf's region centre at the highest non-air block — same convention {@code /glw turf
 * tp} uses, so defenders end up standing on the surface rather than buried in stone or floating in midair.
 *
 * <p>Talks to {@link TurfDefenderDeployer} and {@link TurfPowerupManager} directly (group I, T-I4) — both now live in
 * this module alongside this listener, so the {@code TurfNpcContract}/{@code TurfNpcContracts} cross-module bridge
 * this class used to go through (cops-n-crooks was the only implementor) has no remote side left to bridge to.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class GarrisonDeployListener implements Listener {

	private final GarrisonManager      garrisons;
	private final TurfDefenderDeployer defenders;
	private final TurfDefenderConfig   defenderConfig;
	private final TurfPowerupManager   powerupNpcs;
	private final GangLookupContract   gangs;

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

		int stock = garrisons.count(turf.getId());
		if (stock <= 0) return;

		Location spawn = regionCentreSurface(turf.getRegion());
		if (spawn == null) return;

		int consumed = garrisons.consume(turf.getId(), stock);
		if (consumed <= 0) return;

		defenders.deploy(turf.getId(), spawn, defenderConfig.typeId(), () -> challengerMemberIds(challengerGangId),
		                 consumed, defenderConfig.targetingRadius(), defenderConfig.lifespanSeconds());
	}

	@EventHandler
	public void onCaptured(TurfCapturedEvent event) {
		defenders.recall(event.getTurf().getId());
		powerupNpcs.disengage(event.getTurf().getId());
	}

	@EventHandler
	public void onFailed(TurfCaptureFailedEvent event) {
		defenders.recall(event.getTurf().getId());
		powerupNpcs.disengage(event.getTurf().getId());
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

	private Location regionCentreSurface(CuboidRegion region) {
		World world = Bukkit.getWorld(region.getWorld());
		if (world == null) return null;
		double centreX = (region.getMinX() + region.getMaxX()) / 2.0 + 0.5;
		double centreZ = (region.getMinZ() + region.getMaxZ()) / 2.0 + 0.5;
		int    y       = world.getHighestBlockYAt((int) centreX, (int) centreZ);
		return new Location(world, centreX, y + 1.0, centreZ);
	}
}
