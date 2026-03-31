package me.luckyraven.copsncrooks.npc.police.state.behavior;

import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.npc.police.npc.CopNpc;
import me.luckyraven.copsncrooks.npc.police.state.CopBehavior;
import me.luckyraven.copsncrooks.npc.police.state.CopState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Cop engages the target with weapons. Only entered after escalation or being attacked.
 */
public class CombatBehavior implements CopBehavior {

	private final double            combatRange;
	private final DetainmentService detainmentService;

	public CombatBehavior(double combatRange, DetainmentService detainmentService) {
		this.combatRange       = combatRange;
		this.detainmentService = detainmentService;
	}

	@Override
	public void tick(CopNpc cop) {
		Player target = resolveTarget(cop);
		if (target == null || !target.isOnline()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		if (detainmentService.isRestrained(target)) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		double distance = cop.distanceTo(target);

		double attackRange = cop.getTierConfig().canUseWeapons() ? (combatRange * 3) : combatRange;

		if (distance <= attackRange && cop.canAttack() && cop.hasLineOfSight(target)) {
			cop.attack(target);
		}

		// Ranged cops hold their firing position; melee cops close in
		if (cop.shouldHoldPursuitPosition(target)) {
			cop.stopNavigation();
		} else if (cop.isNavigationHopeless()) {
			cop.navigateTo(cop.resolveHopelessFallbackLocation(target));
		} else {
			cop.navigateTo(target.getLocation());
		}
	}

	@Override
	public void onEnter(CopNpc cop) {
	}

	@Override
	public void onExit(CopNpc cop) {
		cop.stopNavigation();
	}

	private Player resolveTarget(CopNpc cop) {
		UUID id = cop.getTargetPlayerId();
		return id != null ? Bukkit.getPlayer(id) : null;
	}
}