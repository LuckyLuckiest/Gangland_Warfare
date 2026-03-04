package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.police.npc.CopNpc;
import org.bukkit.entity.Player;

/**
 * Cop attempts to cuff the target player. Escalates to combat on repeated failure.
 */
public class CuffingBehavior implements CopBehavior {

	private final double cuffRadius;
	private final int    maxAttempts;

	private int attemptCount;

	public CuffingBehavior(double cuffRadius, int maxAttempts) {
		this.cuffRadius   = cuffRadius;
		this.maxAttempts  = maxAttempts;
		this.attemptCount = 0;
	}

	@Override
	public void tick(CopNpc cop, Player target) {
		if (target == null || !target.isOnline()) {
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		double distance = cop.distanceTo(target);

		if (distance > cuffRadius || !cop.hasLineOfSight(target)) {
			cop.transitionTo(CopState.PURSUING);
			return;
		}

		boolean success = cop.attemptCuff(target);

		if (success) {
			attemptCount = 0;
			cop.transitionTo(CopState.RETURNING);
			return;
		}

		attemptCount++;

		if (attemptCount >= maxAttempts && cop.getTierConfig().canUseWeapons()) {
			cop.transitionTo(CopState.COMBAT);
		}
	}

	@Override
	public void onEnter(CopNpc cop) {
		attemptCount = 0;
		cop.stopNavigation();
	}

	@Override
	public void onExit(CopNpc cop) {
		attemptCount = 0;
	}
}