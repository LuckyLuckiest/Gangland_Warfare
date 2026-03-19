package me.luckyraven.copsncrooks.police.state.behavior;

import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.police.npc.CopNpc;
import me.luckyraven.copsncrooks.police.state.CopBehavior;
import me.luckyraven.copsncrooks.police.state.CopState;
import org.bukkit.Location;
import org.bukkit.entity.Player;

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
	public void tick(CopNpc cop, Player target) {
		if (target == null || !target.isOnline()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		if (detainmentService.isRestrained(target)) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		double distance = cop.distanceTo(target);

		if (distance <= cuffRadius && cop.hasLineOfSight(target)) {
			if (cop.getTierConfig().skipCuffing() || cop.isCombatForced()) {
				cop.transitionTo(CopState.COMBAT);
			} else {
				cop.transitionTo(CopState.CUFFING);
			}
			return;
		}

		// Ranged cops shoot while closing in - they still navigate to cuff range
		if (cop.isUsingRangedWeapon() && cop.hasLineOfSight(target) && cop.canAttack()) {
			cop.attack(target);
		}

		// When pathfinding has repeatedly failed, scan toward the player to find the closest reachable edge before any gap
		if (cop.isNavigationHopeless()) {
			cop.navigateTo(cop.resolveHopelessFallbackLocation(target));
			return;
		}

		Location pursuitTarget = cop.resolvePursuitLocation(target);
		cop.navigateTo(pursuitTarget);
	}

	@Override
	public void onEnter(CopNpc cop) { }

	@Override
	public void onExit(CopNpc cop) {
		cop.stopNavigation();
	}
}