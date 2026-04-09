package me.luckyraven.copsncrooks.npc.police;

import lombok.Getter;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.npc.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.npc.police.npc.CopNpcFactory;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawner;
import me.luckyraven.copsncrooks.npc.police.state.CopBehaviorFactory;
import me.luckyraven.copsncrooks.npc.police.targeting.WantedTargetingManager;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import me.luckyraven.weapon.WeaponService;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class CopService implements BeanLifecycle {

	private CopManager             copManager;
	private WantedTargetingManager targetingManager;

	/**
	 * Initializes and registers all cop system components.
	 *
	 * @param plugin the owning plugin
	 * @param provider the cops configuration file
	 * @param entityMarkManager the entity mark manager for marking cop entities
	 * @param weaponService resolves gangland weapon instances by name
	 *
	 * @return the initialized CopManager
	 */
	public CopManager initialize(JavaPlugin plugin, CopConfigProvider provider, EntityMarkManager entityMarkManager,
	                             WeaponService weaponService, IRepository<CopSpawner> spawnerRepository,
	                             DetainmentService detainmentService) {
		targetingManager = new WantedTargetingManager();

		// Create a placeholder array to capture the spawn manager reference
		CopSpawnManager[] spawnManagerHolder = new CopSpawnManager[1];

		// Create behavior factory with a lazy supplier
		CopBehaviorFactory behaviorFactory = new CopBehaviorFactory(provider, () -> spawnManagerHolder[0],
		                                                            detainmentService);

		// Create NPC factory
		CopNpcFactory copNpcFactory = new CopNpcFactory(plugin, provider, behaviorFactory, entityMarkManager,
		                                                weaponService);

		// Now create spawn manager and store it in the holder
		CopSpawnManager spawnManager = new CopSpawnManager(copNpcFactory, provider, spawnerRepository);
		spawnManagerHolder[0] = spawnManager;

		copManager = new CopManager(plugin, spawnManager, targetingManager, provider, entityMarkManager,
		                            detainmentService);

		return copManager;
	}

	/**
	 * Shuts down all cop systems cleanly.
	 */
	public void shutdown() {
		if (copManager == null) return;

		copManager.shutdown();
	}

	@Override
	public void onShutdown() {
		shutdown();
	}
}