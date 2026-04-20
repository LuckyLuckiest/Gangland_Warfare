package me.luckyraven.copsncrooks.npc.police.state;

import lombok.Getter;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.npc.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.npc.police.state.behavior.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Factory that creates the full set of CopBehavior instances for the state machine.
 */
public class CopBehaviorFactory {

	private final CopConfigProvider         configProvider;
	private final Supplier<CopSpawnManager> spawnManagerSupplier;
	private final DetainmentService         detainmentService;
	@Getter
	private final CuffLockRegistry          cuffLockRegistry;

	public CopBehaviorFactory(CopConfigProvider configProvider, Supplier<CopSpawnManager> spawnManagerSupplier,
	                          DetainmentService detainmentService, CuffLockRegistry cuffLockRegistry) {
		this.configProvider       = configProvider;
		this.spawnManagerSupplier = spawnManagerSupplier;
		this.detainmentService    = detainmentService;
		this.cuffLockRegistry     = cuffLockRegistry;
	}

	/**
	 * Builds a map of all cop states to their behavior implementations.
	 * <p>
	 * All cops created by this factory share the same {@link CuffLockRegistry}, which is keyed by target UUID. This
	 * ensures that only one cop across ALL groups can hold the cuff lock for a given player at any time — including
	 * cops that retargeted from a different group after their original target lost wanted status.
	 *
	 * @return the state-to-behavior map
	 */
	public Map<CopState, CopBehavior> createBehaviors() {
		Map<CopState, CopBehavior> behaviors = new EnumMap<>(CopState.class);

		int aiTickRate = configProvider.getAiTickRate();
		// Convert game-tick cooldown to AI-tick iterations so the countdown completes in the correct wall time.
		int cuffAiTicks = Math.max(1, configProvider.getCuffCooldownTicks() / aiTickRate);

		behaviors.put(CopState.IDLE, new IdleBehavior(configProvider.getAlertRange()));
		behaviors.put(CopState.PURSUING, new PursuingBehavior(configProvider.getCuffRadius(),
		                                                      configProvider.getPursuitMaxDistance(),
		                                                      configProvider.getPursuitMaxTicks(),
		                                                      detainmentService));
		behaviors.put(CopState.CUFFING,
		              new CuffingBehavior(configProvider.getCuffRadius(), configProvider.getMaxCuffAttempts(),
		                                  cuffAiTicks, aiTickRate, cuffLockRegistry, detainmentService));
		behaviors.put(CopState.GUARDING,
		              new GuardingBehavior(configProvider.getGuardRadius(), cuffLockRegistry, detainmentService));
		behaviors.put(CopState.COMBAT, new CombatBehavior(configProvider.getCombatRange(), detainmentService));
		behaviors.put(CopState.RETURNING, new ReturningBehavior(spawnManagerSupplier.get(), detainmentService,
		                                                        configProvider.getMaxReturnTicks(),
		                                                        configProvider.getStationArrivalDistance()));

		return behaviors;
	}
}