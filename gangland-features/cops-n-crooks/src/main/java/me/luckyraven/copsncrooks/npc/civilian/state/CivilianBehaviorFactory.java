package me.luckyraven.copsncrooks.npc.civilian.state;

import me.luckyraven.copsncrooks.npc.civilian.CivilianState;
import me.luckyraven.copsncrooks.npc.civilian.state.behavior.CivilianCombatBehavior;
import me.luckyraven.copsncrooks.npc.civilian.state.behavior.CivilianFleeBehavior;
import me.luckyraven.copsncrooks.npc.civilian.state.behavior.CivilianIdleBehavior;
import me.luckyraven.copsncrooks.npc.civilian.state.behavior.CivilianWanderBehavior;

import java.util.EnumMap;
import java.util.Map;

/**
 * Creates a fresh set of {@link CivilianBehavior} instances for a single civilian NPC. Each NPC receives its own
 * instances so per-NPC tick state (counters, cached locations) is not shared.
 */
public class CivilianBehaviorFactory {

	private final double softLeashRadius;

	public CivilianBehaviorFactory(double softLeashRadius) {
		this.softLeashRadius = softLeashRadius;
	}

	/**
	 * Returns a new {@link EnumMap} mapping every {@link CivilianState} to a behavior instance.
	 */
	public Map<CivilianState, CivilianBehavior> createBehaviors() {
		Map<CivilianState, CivilianBehavior> map = new EnumMap<>(CivilianState.class);
		map.put(CivilianState.IDLE, new CivilianIdleBehavior());
		map.put(CivilianState.WANDERING, new CivilianWanderBehavior(softLeashRadius));
		map.put(CivilianState.FLEEING, new CivilianFleeBehavior());
		map.put(CivilianState.COMBAT, new CivilianCombatBehavior());
		return map;
	}
}
