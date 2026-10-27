package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.UUID;

/**
 * Cop stands at spawn and scans for criminals. Seeing its target within the alert range (with line of sight) reports a
 * sighting to the cop's squad; the cop starts pursuing when it sees the target itself or its squad has a fresh
 * sighting.
 */
public class IdleBehavior implements CopBehavior {

	private final double        alertRange;
	private final CopPerception perception;

	public IdleBehavior(double alertRange) {
		this(alertRange, new CopPerception());
	}

	public IdleBehavior(double alertRange, CopPerception perception) {
		this.alertRange = alertRange;
		this.perception = perception;
	}

	@Override
	public void tick(CopNpc cop) {
		LivingEntity target = resolveTarget(cop);
		if (target == null) return;

		NpcSquad squad = cop.squadFor(target);
		boolean  sees  = perception.acquires(cop, target, alertRange);
		// its own mark at the range it looked with (Keystone 1.15.4): teammates chase only while it still sees him
		if (sees && squad != null)
			squad.reportSighting(cop, target.getLocation(), perception.rangeFor(cop, target, alertRange));

		if (sees || (squad != null && squad.hasFreshSighting())) {
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
