package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;

import java.util.EnumMap;
import java.util.Map;

/**
 * Factory that creates the full set of CopBehavior instances for the state machine.
 */
public class CopBehaviorFactory {

	private final CopConfigProvider configProvider;
	private final CopSpawnManager   spawnManager;

	public CopBehaviorFactory(CopConfigProvider configProvider, CopSpawnManager spawnManager) {
		this.configProvider = configProvider;
		this.spawnManager   = spawnManager;
	}

	/**
	 * Builds a map of all cop states to their behavior implementations.
	 *
	 * @return the state-to-behavior map
	 */
	public Map<CopState, CopBehavior> createBehaviors() {
		Map<CopState, CopBehavior> behaviors = new EnumMap<>(CopState.class);

		behaviors.put(CopState.IDLE, new IdleBehavior(configProvider.getAlertRange()));
		behaviors.put(CopState.PURSUING, new PursuingBehavior(configProvider.getCuffRadius()));
		behaviors.put(CopState.CUFFING,
					  new CuffingBehavior(configProvider.getCuffRadius(), configProvider.getMaxCuffAttempts(),
										  configProvider.getCuffCooldownTicks()));
		behaviors.put(CopState.COMBAT, new CombatBehavior(configProvider.getCombatRange()));
		behaviors.put(CopState.RETURNING, new ReturningBehavior(configProvider.getSpawnLocations(), spawnManager));

		return behaviors;
	}
}