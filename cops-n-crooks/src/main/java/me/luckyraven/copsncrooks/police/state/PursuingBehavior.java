package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.police.npc.CopNpc;
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