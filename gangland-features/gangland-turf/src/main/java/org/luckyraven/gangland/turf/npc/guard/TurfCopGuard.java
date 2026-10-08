package org.luckyraven.gangland.turf.npc.guard;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Turf defenders and the Quartermaster answer a cop that hits a player standing on a turf their gang protects (the
 * turf owner's gang, plus allied gangs when {@code Include_Allies} is on). The cops-n-crooks listener forwards each cop
 * hit to {@link #onCopHitPlayer}; the NPCs on that turf that are hostile and within {@code Targeting_Radius} of the cop
 * get the cop pushed to the front of their entity-target queue. A 5-tick task releases them again when the cop dies,
 * the victim leaves or stops being protected, or the cop moves out of an NPC's range.
 *
 * <p>Main-thread only: the engagement map is touched from listeners and the sync task.
 */
public final class TurfCopGuard {

	private static final long TICK_PERIOD = 5L;

	private final JavaPlugin                plugin;
	private final TurfManager               turfs;
	private final UserLookupContract        users;
	private final GangMembership            membership;
	private final TurfPowerupManager        powerups;
	private final TurfDefenderDeployer      defenders;
	private final CopGuardConfig            config;
	private final Map<UUID, Engagement>     engagements = new HashMap<>();
	private       CopTargetLookup           copTargets  = cop -> null;
	private       BukkitTask                tickTask;

	/**
	 * Reads the player a cop is currently chasing. Supplied by cops-n-crooks through {@link #bindCopTargets}; the turf
	 * module never names a cop type. Returns {@code null} when the cop has no player target (or it is offline).
	 */
	public interface CopTargetLookup {

		Player targetPlayerOf(LivingEntity cop);

	}

	public TurfCopGuard(JavaPlugin plugin,
	                    TurfManager turfs,
	                    UserLookupContract users,
	                    GangMembership membership,
	                    TurfPowerupManager powerups,
	                    TurfDefenderDeployer defenders,
	                    CopGuardConfig config) {
		this.plugin     = plugin;
		this.turfs      = turfs;
		this.users      = users;
		this.membership = membership;
		this.powerups   = powerups;
		this.defenders  = defenders;
		this.config     = config;
	}

	/**
	 * Installs the cop-target seam. Until it is bound the guard assumes no cop has a player target, so engagements are
	 * only dropped by the victim and range rules.
	 */
	public void bindCopTargets(CopTargetLookup lookup) {
		this.copTargets = lookup;
	}

	public void start() {
		if (tickTask != null) return;
		tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
	}

	public void stop() {
		if (tickTask != null) {
			tickTask.cancel();
			tickTask = null;
		}
		engagements.clear();
	}

	/**
	 * A cop hit {@code victim}. If the victim stands on a turf their gang protects, the defenders and Quartermaster of
	 * that turf within range of the cop take it on.
	 */
	public void onCopHitPlayer(LivingEntity cop, Player victim) {
		if (!config.enabled()) return;

		Turf       turf     = turfs.findAt(victim.getLocation());
		Engagement previous = engagements.get(cop.getUniqueId());
		if (!protectedOn(turf, victim)) {
			// The cop is now chasing an unprotected player: stop defending against it.
			if (previous != null) {
				engagements.remove(cop.getUniqueId());
				release(previous);
			}
			return;
		}

		if (previous != null && previous.turfId() != turf.getId()) release(previous);

		engagements.put(cop.getUniqueId(), new Engagement(cop, turf.getId(), victim));
		for (CivilianNpc npc : npcsOf(turf.getId())) {
			if (!npc.isHostile() || distance(npc, cop) > config.radius()) continue;
			npc.addEntityTargetToFront(cop);
			engageCombat(npc);
		}
	}

	/**
	 * Drops engagements whose cop or victim is gone, or whose cop now chases a player the turf does not protect, and
	 * detaches the cop from each NPC that moved out of range.
	 */
	void tick() {
		for (Iterator<Engagement> it = engagements.values().iterator(); it.hasNext(); ) {
			Engagement engagement = it.next();
			LivingEntity cop      = engagement.cop();
			Player       victim   = engagement.victim();

			if (!cop.isValid() || cop.isDead() || !cop.getWorld().equals(victim.getWorld())) {
				release(engagement);
				it.remove();
				continue;
			}
			Turf here = turfs.findAt(victim.getLocation());
			if (!victim.isOnline() || victim.isDead() || DownedPlayerRegistry.isDowned(victim.getUniqueId())
			    || here == null || here.getId() != engagement.turfId() || !protectedOn(here, victim)) {
				release(engagement);
				it.remove();
				continue;
			}
			// The cop switched its chase to someone the turf does not protect: stop defending against it.
			Player chasing = copTargets.targetPlayerOf(cop);
			if (chasing != null && !protectedOn(here, chasing)) {
				release(engagement);
				it.remove();
				continue;
			}

			boolean anyInRange = false;
			for (CivilianNpc npc : npcsOf(engagement.turfId())) {
				if (distance(npc, cop) > config.radius()) {
					detach(npc, cop);
				} else {
					anyInRange = true;
				}
			}
			if (!anyInRange) it.remove();
		}
	}

	/**
	 * Whether {@code victim}'s gang protects {@code turf}: the turf must be claimed and the victim a gang member whose
	 * gang is the owner or (with {@code Include_Allies}) an ally of it.
	 */
	private boolean protectedOn(Turf turf, Player victim) {
		if (turf == null || turf.isUnclaimed()) return false;
		Integer owner = turf.getOwnerGangId();
		User<Player> user = users.findByPlayer(victim);
		if (owner == null || user == null || !user.hasGang()) return false;
		return protects(user.getGangId(), owner, config.includeAllies(), membership::gangsAllied);
	}

	/**
	 * Detaches {@code engagement}'s cop from every NPC of its turf. Does not remove the engagement itself.
	 */
	private void release(Engagement engagement) {
		for (CivilianNpc npc : npcsOf(engagement.turfId())) {
			detach(npc, engagement.cop());
		}
	}

	/**
	 * Removes {@code cop} from {@code npc}'s queue. Only an NPC that actually held the cop is touched, so one that was
	 * never engaged keeps its state; an NPC left with no target at all goes back to IDLE.
	 */
	private static void detach(CivilianNpc npc, LivingEntity cop) {
		if (!npc.removeEntityTarget(cop)) return;
		if (npc.getTargetEntity() == null && npc.getTargetPlayerId() == null) {
			npc.transitionTo(CivilianState.IDLE);
		}
	}

	private static void engageCombat(CivilianNpc npc) {
		if (!npc.getTypeConfig().ai().combatEnabled()) return;
		if (npc.getCurrentState() != CivilianState.COMBAT) npc.transitionTo(CivilianState.COMBAT);
	}

	// ponytail: O(engagements x defenders) per 5 ticks; index by turf if a turf ever holds dozens of defenders.

	/**
	 * The Quartermaster and the live defenders of {@code turfId}.
	 */
	private List<CivilianNpc> npcsOf(int turfId) {
		List<CivilianNpc> npcs = new ArrayList<>(powerups.civilianNpcsOf(turfId));
		npcs.addAll(defenders.liveDefenders(turfId));
		return npcs;
	}

	private static double distance(CivilianNpc npc, LivingEntity cop) {
		LivingEntity body = npc.getEntity();
		if (body == null || !body.getWorld().equals(cop.getWorld())) return Double.MAX_VALUE;
		return body.getLocation().distance(cop.getLocation());
	}

	/**
	 * Whether a victim in {@code victimGangId} is protected by a turf owned by {@code ownerGangId}. A negative id is
	 * the absent sentinel (non-member victim, unclaimed owner) and is never protected.
	 */
	static boolean protects(int victimGangId,
	                        int ownerGangId,
	                        boolean includeAllies,
	                        BiPredicate<Integer, Integer> allied) {
		if (victimGangId < 0 || ownerGangId < 0) return false;
		if (victimGangId == ownerGangId) return true;
		return includeAllies && allied.test(victimGangId, ownerGangId);
	}

	/**
	 * A cop currently held by a turf's NPCs: the victim that brought it there and the turf the victim stood on.
	 */
	private record Engagement(LivingEntity cop, int turfId, Player victim) {
	}
}
