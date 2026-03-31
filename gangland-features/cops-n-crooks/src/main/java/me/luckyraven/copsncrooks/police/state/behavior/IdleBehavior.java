package me.luckyraven.copsncrooks.police.state.behavior;

import me.luckyraven.copsncrooks.police.npc.CopNpc;
import me.luckyraven.copsncrooks.police.state.CopBehavior;
import me.luckyraven.copsncrooks.police.state.CopState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
		Player target = resolveTarget(cop);
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

	private Player resolveTarget(CopNpc cop) {
		UUID id = cop.getTargetPlayerId();
		return id != null ? Bukkit.getPlayer(id) : null;
	}
}