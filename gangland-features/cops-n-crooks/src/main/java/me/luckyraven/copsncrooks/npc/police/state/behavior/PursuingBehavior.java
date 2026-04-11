package me.luckyraven.copsncrooks.npc.police.state.behavior;

import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.npc.police.npc.CopNpc;
import me.luckyraven.copsncrooks.npc.police.state.CopBehavior;
import me.luckyraven.copsncrooks.npc.police.state.CopState;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Cop actively navigates toward a wanted player to attempt cuffing.
 */
public class PursuingBehavior implements CopBehavior {

	private final double            cuffRadius;
	private final DetainmentService detainmentService;

	public PursuingBehavior(double cuffRadius, DetainmentService detainmentService) {
		this.cuffRadius        = cuffRadius;
		this.detainmentService = detainmentService;
	}

	@Override
	public void tick(CopNpc cop) {
		LivingEntity target = resolveTarget(cop);
		if (target == null || !target.isValid() || target.isDead()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		// Restrained check and cuffing only apply to players
		if (target instanceof Player player) {
			if (detainmentService.isRestrained(player)) {
				cop.transitionTo(CopState.RETURNING);
				return;
			}

			double distance = cop.distanceTo(player);

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
			double distance = cop.distanceTo(target);
			if (distance <= cuffRadius && cop.hasLineOfSight(target)) {
				cop.transitionTo(CopState.COMBAT);
				return;
			}

			if (cop.isUsingRangedWeapon() && cop.hasLineOfSight(target) && cop.canAttack()) {
				cop.attackEntity(target);
			}
		}

		// When pathfinding has repeatedly failed, scan toward the target to find the closest reachable edge
		if (cop.isNavigationHopeless()) {
			cop.navigateTo(cop.resolveHopelessFallbackLocation(target));
			return;
		}

		cop.navigateTo(cop.resolvePursuitLocation(target));
	}

	@Override
	public void onEnter(CopNpc cop) {
	}

	@Override
	public void onExit(CopNpc cop) {
		cop.stopNavigation();
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