package org.luckyraven.gangland.copsncrooks.listener.turf;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupNpc;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;

/**
 * Cancels Bartizan weapon-impact friendly fire from turf NPCs, so a defender or Quartermaster's round never hits a
 * protected player of the turf's owning or allied gang. The Bukkit damage half (owner hits and guard-on-guard) lives in
 * the turf module's {@code TurfGuardFriendlyFireListener}, so it works on a server with no cops module. Only the two
 * {@link WeaponRaytraceImpactEvent} handlers need Bartizan, so only they stay here.
 *
 * <p>Damage from non-owning gangs (the actual attackers) passes through untouched and the civilian's normal
 * self-defense kicks in as designed.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfFriendlyFireListener implements Listener {

	private final TurfPowerupManager   powerupNpcs;
	private final TurfDefenderDeployer defenders;
	private final TurfManager          turfs;
	private final UserLookupContract   users;
	private final GangMembership       membership;

	/**
	 * Mirrors the Bukkit owner-hit rule on the canonical weapon-impact event so weapons whose damage path bypasses
	 * {@code EntityDamageByEntityEvent} (flamethrower fire ticks, biological clouds, melee custom handlers, etc.) are
	 * still cancelled when the shooter is a friendly. Cancelling here short-circuits the raytracer's
	 * {@code impactHandler} branch before {@code setFireTicks} / potion application runs.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onWeaponImpact(WeaponRaytraceImpactEvent event) {
		Entity damaged = event.getHitEntity();
		if (damaged == null) return;

		int turfId = resolveTurfId(damaged);
		if (turfId < 0) return;

		Turf turf = turfs.get(turfId);
		if (turf == null || turf.isUnclaimed()) return;
		int ownerGangId = turf.getOwnerGangId();

		if (!(event.getShooter() instanceof Player attacker)) return;

		User<Player> user = users.findByPlayer(attacker);
		if (user == null || !user.hasGang()) return;

		if (!isFriendly(user.getGangId(), ownerGangId)) return;

		event.setCancelled(true);
	}

	/**
	 * Stray rounds: a defender or Quartermaster that fires at a cop often has a protected member standing in the line of
	 * fire, so the shot must not hit that member. Cancels when the shooter is a turf NPC of the turf and the victim is a
	 * player of its owning or allied gang.
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onNpcWeaponImpact(WeaponRaytraceImpactEvent event) {
		if (isNpcFriendlyFire(event.getShooter(), event.getHitEntity())) event.setCancelled(true);
	}

	private boolean isNpcFriendlyFire(Entity shooter, Entity victim) {
		if (shooter == null || victim == null) return false;

		int turfId = resolveTurfId(shooter);
		if (turfId < 0) return false;

		Turf turf = turfs.get(turfId);
		if (turf == null || turf.isUnclaimed()) return false;

		// T-287: guards are Citizens PLAYER entities with no User, so guard-on-guard rounds used to pass.
		int victimTurfId = resolveTurfId(victim);
		if (victimTurfId >= 0) {
			Turf victimTurf = turfs.get(victimTurfId);
			return victimTurf != null && !victimTurf.isUnclaimed() &&
				   isFriendly(turf.getOwnerGangId(), victimTurf.getOwnerGangId());
		}

		if (!(victim instanceof Player target)) return false;

		User<Player> user = users.findByPlayer(target);
		if (user == null || !user.hasGang()) return false;

		return isFriendly(user.getGangId(), turf.getOwnerGangId());
	}

	private boolean isFriendly(int attackerGangId, int ownerGangId) {
		if (attackerGangId == ownerGangId) return true;
		return membership.gangsAllied(attackerGangId, ownerGangId);
	}

	private int resolveTurfId(Entity damaged) {
		TurfPowerupNpc qm = powerupNpcs.getByEntity(damaged);
		if (qm != null) return qm.getData().getTurfId();
		return defenders.findOwningTurfId(damaged);
	}
}
