package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.police.npc.CopNpc;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Cop actively navigates toward a wanted player to attempt cuffing.
 */
public class PursuingBehavior implements CopBehavior {

	private final double cuffRadius;

	public PursuingBehavior(double cuffRadius) {
		this.cuffRadius = cuffRadius;
	}

	@Override
	public void tick(CopNpc cop, Player target) {
		if (target == null || !target.isOnline()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		double distance = cop.distanceTo(target);

		if (distance <= cuffRadius && cop.hasLineOfSight(target)) {
			cop.transitionTo(CopState.CUFFING);
			return;
		}

		if (cop.shouldHoldPursuitPosition(target)) {
			cop.stopNavigation();
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