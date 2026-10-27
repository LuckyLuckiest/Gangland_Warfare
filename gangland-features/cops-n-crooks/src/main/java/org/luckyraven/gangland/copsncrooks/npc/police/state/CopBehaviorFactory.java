package org.luckyraven.gangland.copsncrooks.npc.police.state;

import lombok.Getter;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.behavior.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
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
	/** Whether a player is on the post-escape search; a searched player is never cuffed (0.16.1 T-187). */
	private final Predicate<UUID>           searching;
	/** Sight decisions for the idle, pursuing and combat behaviours (0.20.0 seam). */
	private final CopPerception             perception;
	/** The beat cops' services (0.20.0 lane patrol); {@code null}: PATROL and WARNING stay inert. */
	private final PatrolServices            patrolServices;

	public CopBehaviorFactory(CopConfigProvider configProvider, Supplier<CopSpawnManager> spawnManagerSupplier,
	                          DetainmentService detainmentService, CuffLockRegistry cuffLockRegistry,
	                          Predicate<UUID> searching) {
		this(configProvider, spawnManagerSupplier, detainmentService, cuffLockRegistry, searching, new CopPerception());
	}

	public CopBehaviorFactory(CopConfigProvider configProvider, Supplier<CopSpawnManager> spawnManagerSupplier,
	                          DetainmentService detainmentService, CuffLockRegistry cuffLockRegistry,
	                          Predicate<UUID> searching, CopPerception perception) {
		this(configProvider, spawnManagerSupplier, detainmentService, cuffLockRegistry, searching, perception, null);
	}

	public CopBehaviorFactory(CopConfigProvider configProvider, Supplier<CopSpawnManager> spawnManagerSupplier,
	                          DetainmentService detainmentService, CuffLockRegistry cuffLockRegistry,
	                          Predicate<UUID> searching, CopPerception perception,
	                          @org.jetbrains.annotations.Nullable PatrolServices patrolServices) {
		this.patrolServices      = patrolServices;
		this.perception          = perception;
		this.configProvider       = configProvider;
		this.spawnManagerSupplier = spawnManagerSupplier;
		this.detainmentService    = detainmentService;
		this.cuffLockRegistry     = cuffLockRegistry;
		this.searching            = searching;
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

		behaviors.put(CopState.IDLE, new IdleBehavior(configProvider.getAlertRange(), perception));
		behaviors.put(CopState.PURSUING, new PursuingBehavior(configProvider.getCuffRadius(),
		                                                      configProvider.getAlertRange(),
		                                                      configProvider.getPursuitMaxDistance(),
		                                                      configProvider.getPursuitMaxTicks(),
		                                                      detainmentService, cuffLockRegistry,
		                                                      configProvider.getRetreatSettings(),
		                                                      System::currentTimeMillis, searching, perception));
		behaviors.put(CopState.CUFFING,
		              new CuffingBehavior(configProvider.getCuffRadius(), configProvider.getMaxCuffAttempts(),
		                                  cuffAiTicks, aiTickRate, cuffLockRegistry, detainmentService, searching));
		behaviors.put(CopState.GUARDING,
		              new GuardingBehavior(configProvider.getGuardRadius(), cuffLockRegistry, detainmentService));
		behaviors.put(CopState.COMBAT, new CombatBehavior(configProvider.getCombatRange(), configProvider.getAlertRange(),
		                                                  detainmentService, configProvider.getRetreatSettings(),
		                                                  perception));
		behaviors.put(CopState.RETURNING, new ReturningBehavior(spawnManagerSupplier.get(), detainmentService,
		                                                        configProvider.getMaxReturnTicks(),
		                                                        configProvider.getStationArrivalDistance(),
		                                                        configProvider.getStuckSettings(),
		                                                        configProvider.getVisibilityCheckDistance(),
		                                                        configProvider.getReturnWatchedCapSeconds()));

		behaviors.put(CopState.POSTED, new PostedBehavior());
		behaviors.put(CopState.PATROL, new PatrolBehavior(patrolServices));
		behaviors.put(CopState.WARNING, new WarningBehavior(patrolServices));
		behaviors.put(CopState.OVERWATCH, new OverwatchBehavior());

		return behaviors;
	}
}