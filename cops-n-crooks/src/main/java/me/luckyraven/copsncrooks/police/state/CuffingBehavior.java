package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.events.police.CuffedEvent;
import me.luckyraven.copsncrooks.events.police.DuringCuffingEvent;
import me.luckyraven.copsncrooks.police.npc.CopNpc;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Cop attempts to cuff the target player. Escalates to combat on repeated failure.
 */
public class CuffingBehavior implements CopBehavior {

	private final double cuffRadius;
	private final int    maxAttempts;
	private final long   cuffingCooldown;

	private int  attemptCount;
	private long cuffingTicks;

	public CuffingBehavior(double cuffRadius, int maxAttempts, long cuffingCooldown) {
		this.cuffRadius      = cuffRadius;
		this.maxAttempts     = maxAttempts;
		this.cuffingCooldown = cuffingCooldown;

		reset();
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

		if (cuffingTicks > 0) {
			// Still in the cuffing wind-up
			var duringCuffingEvent = new DuringCuffingEvent(cop, target, cuffRadius, maxAttempts, cuffingCooldown,
															cuffingTicks);
			Bukkit.getPluginManager().callEvent(duringCuffingEvent);

			cuffingTicks--;

			return;
		}

		// Cooldown expired, attempt the cuff
		boolean success = cop.attemptCuff(target);

		if (success) {
			attemptCount = 0;
			var cuffedEvent = new CuffedEvent(cop, target, cuffRadius, maxAttempts);

			Bukkit.getPluginManager().callEvent(cuffedEvent);
			cop.transitionTo(CopState.RETURNING);

			return;
		}

		attemptCount++;
		cuffingTicks = cuffingCooldown;

		if (!(attemptCount >= maxAttempts && cop.getTierConfig().canUseWeapons())) return;

		cop.transitionTo(CopState.COMBAT);
	}

	@Override
	public void onEnter(CopNpc cop) {
		reset();

		cop.stopNavigation();
	}

	@Override
	public void onExit(CopNpc cop) {
		reset();
	}

	private void reset() {
		this.attemptCount = 0;
		this.cuffingTicks = cuffingCooldown;
	}
}