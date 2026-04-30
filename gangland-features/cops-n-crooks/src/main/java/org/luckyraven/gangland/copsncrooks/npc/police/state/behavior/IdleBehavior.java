package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;

import java.util.UUID;

/**
 * Cop stands at spawn and scans for criminals.
 */
public class IdleBehavior implements CopBehavior {

	private final double alertRange;

	public IdleBehavior(double alertRange) {
		this.alertRange = alertRange;
	}

	@Override
	public void tick(CopNpc cop) {
		LivingEntity target = resolveTarget(cop);
		if (target == null) return;

		double distance = cop.distanceTo(target);
		if (distance <= alertRange && cop.hasLineOfSight(target)) {
			cop.transitionTo(CopState.PURSUING);
		}
	}

	@Override
	public void onEnter(CopNpc cop) {
		cop.stopNavigation();
	}

	@Override
	public void onExit(CopNpc cop) {
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