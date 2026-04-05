package me.luckyraven.copsncrooks.npc.civilian.state.behavior;

import me.luckyraven.copsncrooks.npc.civilian.CivilianState;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.copsncrooks.npc.civilian.state.CivilianBehavior;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Idle behavior: the civilian stands still. After a random delay it transitions to {@link CivilianState#WANDERING} if
 * wandering is enabled for its type, otherwise it resets the countdown and stays idle.
 */
public class CivilianIdleBehavior implements CivilianBehavior {

	private static final int MIN_IDLE_TICKS = 40;
	private static final int MAX_IDLE_TICKS = 100;

	private int idleCountdown;

	@Override
	public void onEnter(CivilianNpc npc) {
		idleCountdown = ThreadLocalRandom.current().nextInt(MIN_IDLE_TICKS, MAX_IDLE_TICKS + 1);
	}

	@Override
	public void tick(CivilianNpc npc) {
		idleCountdown--;

		if (idleCountdown > 0) return;

		if (npc.getTypeConfig().ai().wanderEnabled()) {
			npc.transitionTo(CivilianState.WANDERING);
		} else {
			idleCountdown = ThreadLocalRandom.current().nextInt(MIN_IDLE_TICKS, MAX_IDLE_TICKS + 1);
		}
	}

	@Override
	public void onExit(CivilianNpc npc) {
		// nothing to release
	}
}
