package me.luckyraven.copsncrooks.npc.civilian.state.behavior;

import me.luckyraven.copsncrooks.npc.civilian.CivilianState;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.copsncrooks.npc.civilian.state.CivilianBehavior;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Idle behavior: the civilian stands still, periodically looks around, and eventually decides whether to wander or
 * linger longer.
 * <p>
 * If a remembered combat target ({@link CivilianNpc#getTargetPlayerId()}) comes back within re-engage range the NPC
 * transitions straight to {@link CivilianState#COMBAT} without waiting for the idle countdown to expire.
 */
public class CivilianIdleBehavior implements CivilianBehavior {

	private static final int MIN_IDLE_TICKS = 40;
	private static final int MAX_IDLE_TICKS = 100;
	private static final int MIN_LOOK_TICKS = 15;
	private static final int MAX_LOOK_TICKS = 35;
	private static final int LOOK_RADIUS    = 12;

	private int idleCountdown;
	private int lookCountdown;

	@Override
	public void onEnter(CivilianNpc npc) {
		idleCountdown = ThreadLocalRandom.current().nextInt(MIN_IDLE_TICKS, MAX_IDLE_TICKS + 1);
		lookCountdown = ThreadLocalRandom.current().nextInt(MIN_LOOK_TICKS, MAX_LOOK_TICKS + 1);
	}

	@Override
	public void tick(CivilianNpc npc) {
		// Re-engage a remembered combat target that has returned within range
		UUID targetId = npc.getTargetPlayerId();
		if (targetId != null) {
			Player target = Bukkit.getPlayer(targetId);
			if (target != null && target.isOnline()) {
				double reEngageRange = npc.getTypeConfig().ai().attackRange() * 2;
				if (npc.distanceTo(target) <= reEngageRange) {
					npc.transitionTo(CivilianState.COMBAT);
					return;
				}
			} else {
				npc.setTargetPlayerId(null);
			}
		}

		// Ambient look-around
		lookCountdown--;
		if (lookCountdown <= 0) {
			lookCountdown = ThreadLocalRandom.current().nextInt(MIN_LOOK_TICKS, MAX_LOOK_TICKS + 1);
			CivilianLookController.lookAtNearbyEntityOrRandom(npc, LOOK_RADIUS);
		}

		// State-transition countdown
		idleCountdown--;
		if (idleCountdown > 0) return;

		if (npc.getTypeConfig().ai().wanderEnabled()) {
			// 70% chance to wander, 30% chance to linger
			if (ThreadLocalRandom.current().nextInt(10) < 7) {
				npc.transitionTo(CivilianState.WANDERING);
			} else {
				idleCountdown = ThreadLocalRandom.current().nextInt(MIN_IDLE_TICKS, MAX_IDLE_TICKS + 1);
			}
		} else {
			idleCountdown = ThreadLocalRandom.current().nextInt(MIN_IDLE_TICKS, MAX_IDLE_TICKS + 1);
		}
	}

	@Override
	public void onExit(CivilianNpc npc) {
		// nothing to release
	}
}
