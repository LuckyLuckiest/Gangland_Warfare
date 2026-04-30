package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;

import java.util.UUID;

/**
 * Cop actively navigates toward a wanted player to attempt cuffing.
 */
public class PursuingBehavior implements CopBehavior {

	private final double            cuffRadius;
	private final double            maxPursuitDistance;
	private final int               maxPursuitTicks;
	private final DetainmentService detainmentService;

	public PursuingBehavior(double cuffRadius, double maxPursuitDistance, int maxPursuitTicks,
	                        DetainmentService detainmentService) {
		this.cuffRadius         = cuffRadius;
		this.maxPursuitDistance = maxPursuitDistance;
		this.maxPursuitTicks    = maxPursuitTicks;
		this.detainmentService  = detainmentService;
	}

	@Override
	public void tick(CopNpc cop) {
		LivingEntity target = resolveTarget(cop);
		if (target == null || !target.isValid() || target.isDead()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		// Give up if we've been pursuing too long — stuck/unreachable cops must free the spawn cap
		cop.setPursuitTicks(cop.getPursuitTicks() + 1);
		if (cop.getPursuitTicks() >= maxPursuitTicks) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		double distance = cop.distanceTo(target);

		// Hard distance leash — target outran us or teleported beyond our reach
		if (distance > maxPursuitDistance) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		// Restrained check and cuffing only apply to players
		if (target instanceof Player player) {
			if (detainmentService.isRestrained(player)) {
				cop.transitionTo(CopState.RETURNING);
				return;
			}

			if (distance <= cuffRadius && cop.hasLineOfSight(player)) {
				if (cop.getTierConfig().skipCuffing() || cop.isCombatForced()) {
					cop.transitionTo(CopState.COMBAT);
				} else {
					cop.transitionTo(CopState.CUFFING);
				}
				return;
			}

			// Ranged cops shoot while closing in
			if (cop.isUsingRangedWeapon() && cop.hasLineOfSight(player) && cop.canAttack()) {
				cop.attack(player);
			}
		} else {
			// Entity target (hostile civilian NPC): go straight to COMBAT once in cuff range
			if (distance <= cuffRadius && cop.hasLineOfSight(target)) {
				cop.transitionTo(CopState.COMBAT);
				return;
			}

			if (cop.isUsingRangedWeapon() && cop.hasLineOfSight(target) && cop.canAttack()) {
				cop.attackEntity(target);
			}
		}

		// Pathfinding has permanently given up — return instead of flailing toward unreachable fallbacks
		if (cop.isNavigationHopeless()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		cop.navigateTo(cop.resolvePursuitLocation(target));
	}

	@Override
	public void onEnter(CopNpc cop) {
		cop.setPursuitTicks(0);
	}

	@Override
	public void onExit(CopNpc cop) {
		cop.stopNavigation();
		cop.setPursuitTicks(0);
	}

	private LivingEntity resolveTarget(CopNpc cop) {
		UUID id = cop.getTargetPlayerId();
		if (id != null) {
			return Bukkit.getPlayer(id);
		}
		LivingEntity entity = cop.getTargetEntity();
		return (entity != null && entity.isValid() && !entity.isDead()) ? entity : null;
	}
}