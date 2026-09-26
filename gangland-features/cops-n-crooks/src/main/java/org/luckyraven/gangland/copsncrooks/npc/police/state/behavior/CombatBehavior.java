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
 * Cop engages the target with weapons. Only entered after escalation or being attacked.
 */
public class CombatBehavior implements CopBehavior {

	private final double            combatRange;
	private final double            alertRange;
	private final DetainmentService detainmentService;

	public CombatBehavior(double combatRange, double alertRange, DetainmentService detainmentService) {
		this.combatRange       = combatRange;
		this.alertRange        = alertRange;
		this.detainmentService = detainmentService;
	}

	@Override
	public void tick(CopNpc cop) {
		LivingEntity target = resolveTarget(cop);
		if (target == null || !target.isValid() || target.isDead()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		// Restrained check only applies to players
		if (target instanceof Player player && detainmentService.isRestrained(player)) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		double distance    = cop.distanceTo(target);
		double attackRange = cop.getTierConfig().canUseWeapons() ? (combatRange * 3) : combatRange;

		if (distance <= attackRange && cop.canAttack() && cop.hasLineOfSight(target)) {
			if (target instanceof Player player) {
				cop.attack(player);
			} else {
				cop.attackEntity(target);
			}
		}

		// Keystone's squad pursuit: ranged cops hold while they see the target inside their firing band, everyone else
		// closes in, routes around obstacles or searches from the last-known position. squadFor returns null for an
		// entity target (hostile NPC), which then uses the cop's private squad.
		cop.pursue(target, cop.squadFor(target), alertRange);
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
