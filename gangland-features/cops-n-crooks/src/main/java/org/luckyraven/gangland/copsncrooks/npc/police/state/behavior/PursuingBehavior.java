package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Cop actively navigates toward a wanted player to attempt cuffing.
 * <p>
 * Navigation is Keystone's squad pursuit ({@code cop.pursue}): chase while anyone in the squad sees the target, route
 * around obstacles, search from the last-known position, wait below an unreachable target. A failed path never sends
 * the cop back. It is rotated out ({@link CopState#RETURNING}, replaced by the spawner) only after
 * {@code maxPursuitTicks} AI ticks stuck while nobody in its squad sees the target, or when the target is farther than
 * {@code maxPursuitDistance}.
 */
public class PursuingBehavior implements CopBehavior {

	private final double            cuffRadius;
	private final double            alertRange;
	private final double            maxPursuitDistance;
	private final int               maxPursuitTicks;
	private final DetainmentService detainmentService;
	private final CuffLockRegistry  cuffLocks;
	private final CopRetreat        retreat;

	public PursuingBehavior(double cuffRadius, double alertRange, double maxPursuitDistance, int maxPursuitTicks,
	                        DetainmentService detainmentService, CuffLockRegistry cuffLocks, RetreatSettings retreat) {
		this(cuffRadius, alertRange, maxPursuitDistance, maxPursuitTicks, detainmentService, cuffLocks, retreat,
		     System::currentTimeMillis);
	}

	PursuingBehavior(double cuffRadius, double alertRange, double maxPursuitDistance, int maxPursuitTicks,
	                 DetainmentService detainmentService, CuffLockRegistry cuffLocks, RetreatSettings retreat,
	                 LongSupplier clock) {
		this.cuffRadius         = cuffRadius;
		this.alertRange         = alertRange;
		this.maxPursuitDistance = maxPursuitDistance;
		this.maxPursuitTicks    = maxPursuitTicks;
		this.detainmentService  = detainmentService;
		this.cuffLocks          = cuffLocks;
		this.retreat            = new CopRetreat(retreat, clock);
	}

	@Override
	public void tick(CopNpc cop) {
		LivingEntity target = resolveTarget(cop);
		if (target == null || !target.isValid() || target.isDead()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		NpcSquad squad = cop.squadFor(target);

		// Rotation: only ticks spent stuck while nobody in the squad sees the target count toward giving up
		// (an entity target has no shared squad, so the cop's own sight decides)
		boolean seen       = squad != null ? squad.hasFreshSighting() : cop.canSee(target, alertRange);
		int     stuckTicks = cop.isNavigationStuck() && !seen ? cop.getPursuitTicks() + 1 : 0;
		cop.setPursuitTicks(stuckTicks);
		if (stuckTicks >= maxPursuitTicks) {
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
					return;
				}
				// Another officer is cuffing him: hold this cop's surround post instead of bouncing off the lock
				if (!cuffLocks.isHeldByOther(player.getUniqueId(), cop.getNpc().getUniqueId())) {
					cop.transitionTo(CopState.CUFFING);
					return;
				}
			}

			// Ranged cops shoot while closing in
			if (cop.isRangedAttacker() && cop.hasLineOfSight(player) && cop.canAttack()) {
				cop.attack(player);
			}
		} else {
			// Entity target (hostile civilian NPC): go straight to COMBAT once in cuff range
			if (distance <= cuffRadius && cop.hasLineOfSight(target)) {
				cop.transitionTo(CopState.COMBAT);
				return;
			}

			if (cop.isRangedAttacker() && cop.hasLineOfSight(target) && cop.canAttack()) {
				cop.attackEntity(target);
			}
		}

		// A hurt shooter fights from PURSUING (a band cop never reaches COMBAT unless attacked), so it retreats here as
		// in COMBAT. Melee cops chase to cuff rather than fight; cuff range was already handled above.
		if (cop.isRangedAttacker() && retreat.takeCover(cop, target)) return;

		cop.pursue(target, squad, alertRange);
	}

	@Override
	public void onEnter(CopNpc cop) {
		cop.setPursuitTicks(0);
	}

	@Override
	public void onExit(CopNpc cop) {
		retreat.reset(cop);
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
