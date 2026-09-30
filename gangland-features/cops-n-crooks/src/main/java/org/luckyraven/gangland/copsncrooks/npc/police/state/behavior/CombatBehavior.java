package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.npc.RetreatSettings;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Cop engages the target with weapons. Only entered after escalation or being attacked.
 */
public class CombatBehavior implements CopBehavior {

	private final double            combatRange;
	private final double            alertRange;
	private final DetainmentService detainmentService;
	private final CopRetreat        retreat;

	public CombatBehavior(double combatRange, double alertRange, DetainmentService detainmentService,
	                      RetreatSettings retreat) {
		this(combatRange, alertRange, detainmentService, retreat, System::currentTimeMillis);
	}

	CombatBehavior(double combatRange, double alertRange, DetainmentService detainmentService, RetreatSettings retreat,
	               LongSupplier clock) {
		this.combatRange       = combatRange;
		this.alertRange        = alertRange;
		this.detainmentService = detainmentService;
		this.retreat           = new CopRetreat(retreat, clock);
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

		// A ranged cop fires at anything it can see within its sight range, as in PURSUING: its firing band reaches
		// past Combat_Range, and a narrower gate would leave a stretch of the band where it holds without firing.
		// Melee tiers start a swing within Combat_Range; it lands only within the Keystone melee reach.
		double distance    = cop.distanceTo(target);
		double attackRange = cop.isRangedAttacker() ? alertRange : combatRange;

		if (distance <= attackRange && cop.canAttack() && cop.hasLineOfSight(target)) {
			if (target instanceof Player player) {
				cop.attack(player);
			} else {
				cop.attackEntity(target);
			}
		}

		if (holdsForFieldCare(cop)) return;

		// Badly hurt: break off to cover and keep firing from there when seen, for at most CopRetreat.MAX_COVER_MS per
		// COMBAT episode. No cover within the radius (open ground): keep fighting rather than freeze on the spot.
		if (retreat.takeCover(cop, target)) return;

		// Keystone's squad pursuit: ranged cops work their post on the squad's fan while they see the target inside
		// their firing band, everyone else closes in, routes around obstacles or searches from the last-known position.
		// squadFor gives the group squad for the group's player and the group's shared squad against anyone else.
		cop.pursue(target, cop.squadFor(target), alertRange);
	}

	@Override
	public void onEnter(CopNpc cop) {
	}

	@Override
	public void onExit(CopNpc cop) {
		retreat.reset(cop);
		cop.stopNavigation();
	}

	/**
	 * Field care moves this cop, never the fight: a medic with a patient is walked by {@code CopFieldCare}, and a
	 * patient under care holds still. Both still fire (the attack above has already run).
	 */
	static boolean holdsForFieldCare(CopNpc cop) {
		if (cop.getPatient() != null) return true;
		if (!cop.isUnderCare()) return false;
		cop.pauseNavigation();
		return true;
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
