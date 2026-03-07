package me.luckyraven.copsncrooks.police;

import lombok.Getter;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.listener.CopListener;
import me.luckyraven.copsncrooks.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.police.npc.CopNpcFactory;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.police.targeting.TargetingManager;
import me.luckyraven.copsncrooks.police.targeting.WantedTargetingManager;
import me.luckyraven.weapon.WeaponService;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class CopService {

	private CopManager copManager;

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
								 WeaponService weaponService) {
		TargetingManager targetingManager = new WantedTargetingManager();

		// Two-phase init: CopSpawnManager and CopNpcFactory mutually reference each other
		CopSpawnManager spawnManager = new CopSpawnManager(null, provider);
		CopNpcFactory copNpcFactory = new CopNpcFactory(provider, entityMarkManager, spawnManager, plugin,
														weaponService);

		spawnManager  = new CopSpawnManager(copNpcFactory, provider);
		copNpcFactory = new CopNpcFactory(provider, entityMarkManager, spawnManager, plugin, weaponService);
		spawnManager  = new CopSpawnManager(copNpcFactory, provider);

		copManager = new CopManager(plugin, spawnManager, targetingManager, provider, entityMarkManager);

		CopListener listener = new CopListener(copManager);
		plugin.getServer().getPluginManager().registerEvents(listener, plugin);

		return copManager;
	}

	/**
	 * Shuts down all cop systems cleanly.
	 */
	public void shutdown() {
		if (copManager == null) return;

		copManager.shutdown();
	}
}