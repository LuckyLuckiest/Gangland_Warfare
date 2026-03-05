package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.police.npc.CopNpc;
import org.bukkit.entity.Player;

/**
 * Cop engages the target with weapons. Only entered after escalation or being attacked.
 */
public class CombatBehavior implements CopBehavior {

	private final double combatRange;

	public CombatBehavior(double combatRange) {
		this.combatRange = combatRange;
	}

	@Override
	public void tick(CopNpc cop, Player target) {
		if (target == null || !target.isOnline()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		double distance = cop.distanceTo(target);

		if (distance > combatRange * 3) {
			cop.transitionTo(CopState.PURSUING);
			return;
		}

		double attackRange = cop.getTierConfig().canUseWeapons() ? (combatRange * 3) : combatRange;

		if (distance <= attackRange && cop.canAttack() && cop.hasLineOfSight(target)) {
			cop.attack(target);
		}

		cop.navigateTo(target.getLocation());
	}

	@Override
	public void onEnter(CopNpc cop) {
	}

	@Override
	public void onExit(CopNpc cop) {
		cop.stopNavigation();
	}
}