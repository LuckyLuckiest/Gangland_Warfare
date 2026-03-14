package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Factory that creates the full set of CopBehavior instances for the state machine.
 */
public class CopBehaviorFactory {

	private final CopConfigProvider         configProvider;
	private final Supplier<CopSpawnManager> spawnManagerSupplier;
	private final DetainmentService         detainmentService;
	/**
	 * Shared across every cop created by this factory — only one cop may cuff a given player at a time.
	 */
	private final Set<UUID>                 cuffLock = ConcurrentHashMap.newKeySet();

	public CopBehaviorFactory(CopConfigProvider configProvider, Supplier<CopSpawnManager> spawnManagerSupplier,
							  DetainmentService detainmentService) {
		this.configProvider       = configProvider;
		this.spawnManagerSupplier = spawnManagerSupplier;
		this.detainmentService    = detainmentService;
	}

	/**
	 * Builds a map of all cop states to their behavior implementations.
	 *
	 * @return the state-to-behavior map
	 */
	public Map<CopState, CopBehavior> createBehaviors() {
		Map<CopState, CopBehavior> behaviors = new EnumMap<>(CopState.class);

		int aiTickRate = configProvider.getAiTickRate();
		// Convert game-tick cooldown to AI-tick iterations so the countdown completes in the correct wall time.
		int cuffAiTicks = Math.max(1, configProvider.getCuffCooldownTicks() / aiTickRate);

		behaviors.put(CopState.IDLE, new IdleBehavior(configProvider.getAlertRange()));
		behaviors.put(CopState.PURSUING, new PursuingBehavior(configProvider.getCuffRadius(), detainmentService));
		behaviors.put(CopState.CUFFING,
					  new CuffingBehavior(configProvider.getCuffRadius(), configProvider.getMaxCuffAttempts(),
										  cuffAiTicks, aiTickRate, cuffLock, detainmentService));
		behaviors.put(CopState.COMBAT, new CombatBehavior(configProvider.getCombatRange(), detainmentService));
		behaviors.put(CopState.RETURNING,
					  new ReturningBehavior(configProvider.getSpawnLocations(), spawnManagerSupplier.get()));

		return behaviors;
	}
}