package org.luckyraven.gangland.turf.listener.powerups;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureStartEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.config.TurfNpcsConfigLoader;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderConfig;
import org.luckyraven.gangland.turf.npc.defender.GuardCallsign;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.turf.npc.defender.TurfGarrison;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;

import java.util.HashSet;
import java.util.function.IntSupplier;
import java.util.Set;
import java.util.UUID;

/**
 * Arms the owning gang's pre-purchased defender garrison the moment an enemy starts capturing the turf, and pivots the
 * per-turf Quartermaster NPC into hostile mode for the duration of the contest. The garrison is a reserve: the
 * deployer spawns it in waves at hidden posts and takes each guard out of the reserve only after it spawned, so
 * nothing is spent at contest start. When a held contest ends the survivors stand down and return to the reserve,
 * capped at Garrison.Max_Reserve; the guards that never spawned were never taken. A completed capture stands the
 * guards down as lost, and the owner change that follows recalls them and resets the old owner's stock (T-270).
 */
@ListenerHandler
@RequiredArgsConstructor
public final class GarrisonDeployListener implements Listener {

	private final GarrisonManager      garrisons;
	private final TurfDefenderDeployer defenders;
	private final TurfNpcsConfigLoader npcConfig;
	private final TurfPowerupManager   powerupNpcs;
	private final GangLookupContract   gangs;
	private final TurfRules            rules;
	private final TurfMessages        text;

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

		int turfId = turf.getId();
		if (garrisons.count(turfId) <= 0) return;

		// TF-36: read the live config at deploy time, so a /glw reload of turf_npcs.yml applies to the next contest.
		TurfDefenderConfig defenderConfig = npcConfig.getDefenderConfig();
		Gang owner = gangs.findById(turf.getOwnerGangId());
		String tag = owner == null ? GuardCallsign.tag("", "")
		                           : GuardCallsign.tag(GangDisplayNameResolver.resolve(owner), owner.getName());
		// TF-20/TF-46: the deployer spawns the guards in waves and takes each one out of the reserve only after it
		// spawned, so nothing is spent here.
		TurfGarrison garrison = new TurfGarrison(turfId, turf.getRegion(), defenderConfig, tag,
		                                         () -> garrisons.count(turfId), n -> garrisons.consume(turfId, n),
		                                         n -> tellOwners(turf, "Garrison.Wave",
		                                                         "&7%count% guards took their posts at &f%turf%&7.", n));
		// r2: guards must outlive the longest contest (a stall runs to Max_Contest_Minutes), or they expire first and the
		// survivors that return to the reserve are already gone. T-269: the supplier is read on every deployer tick, so a
		// /glw reload of turf_npcs.yml or turf_rules.yml reaches guards already in the field.
		IntSupplier lifespan = () -> Math.max(npcConfig.getDefenderConfig().lifespanSeconds(),
		                                      rules.capture().maxContestMinutes() * 60 + 60);
		defenders.startGarrison(garrison, () -> challengerMemberIds(challengerGangId), lifespan);
	}

	@EventHandler
	public void onCaptured(TurfCapturedEvent event) {
		// the old owner's stock is reset by the owner change that follows, so nothing returns to it
		defenders.standDown(event.getTurf().getId(), false, survivors -> {
		});
		powerupNpcs.disengage(event.getTurf().getId());
	}

	/**
	 * T-270: garrison stock belongs to the gang that bought it. A turf that changes hands for any reason (capture, admin,
	 * disband, inactivity) recalls the old owner's guards and starts with no stock, so a give-back never reaches the new owner.
	 */
	@EventHandler
	public void onOwnerChanged(TurfOwnerChangedEvent event) {
		int turfId = event.getTurf().getId();
		defenders.recall(turfId);
		garrisons.remove(turfId);
	}

	/**
	 * A failed contest ends the fight. On a turf the owner still holds, the guards that survive stand down and go back to
	 * the reserve (capped at Garrison.Max_Reserve); the guards that never spawned were never taken out of it.
	 */
	@EventHandler
	public void onFailed(TurfCaptureFailedEvent event) {
		Turf turf = event.getTurf();
		defenders.standDown(turf.getId(), true, survivors -> returnToReserve(turf, survivors));
		powerupNpcs.disengage(turf.getId());
	}

	/** r2: survivors return to the reserve only up to Max_Reserve, so the give-back never mints guards past the cap. */
	private void returnToReserve(Turf turf, int survivors) {
		if (turf.isUnclaimed()) return;
		int added = garrisons.refund(turf.getId(), survivors, rules.getMaxReserve());
		if (added > 0) {
			tellOwners(turf, "Garrison.Returned", "&7%count% guards back in the reserve at &f%turf%&7.", added);
		}
	}

	/** One of the owner's garrison lines to its online members. */
	private void tellOwners(Turf turf, String key, String fallback, int count) {
		Integer ownerId = turf.getOwnerGangId();
		Gang    owner   = ownerId == null ? null : gangs.findById(ownerId);
		if (owner == null) return;
		String line = text.text(key, fallback, "turf", turf.getDisplayName(), "count", count);
		if (line == null || line.isEmpty()) return;
		for (Member member : owner.getMembers()) {
			Player player = Bukkit.getPlayer(member.getUuid());
			if (player != null) {
				player.sendMessage(line);
			}
		}
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
