package me.luckyraven.copsncrooks.police;

import lombok.Getter;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.listener.CopListener;
import me.luckyraven.copsncrooks.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.police.config.YamlCopConfigProvider;
import me.luckyraven.copsncrooks.police.npc.CopNpcFactory;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.police.targeting.TargetingManager;
import me.luckyraven.copsncrooks.police.targeting.WantedTargetingManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class CopService {

	private CopManager copManager;

	/**
	 * Initializes and registers all cop system components.
	 *
	 * @param plugin the owning plugin
	 * @param config the cops configuration file
	 * @param entityMarkManager the entity mark manager for marking cop entities
	 *
	 * @return the initialized CopManager
	 */
	public CopManager initialize(JavaPlugin plugin, FileConfiguration config, EntityMarkManager entityMarkManager) {
		CopConfigProvider configProvider   = new YamlCopConfigProvider(config);
		TargetingManager  targetingManager = new WantedTargetingManager();

		// CopSpawnManager is created first but needs the factory — use two-phase init
		CopNpcFactory   copNpcFactory;
		CopSpawnManager spawnManager = new CopSpawnManager(null, configProvider);

		copNpcFactory = new CopNpcFactory(configProvider, entityMarkManager, spawnManager);

		// Now reconstruct the spawn manager with the real factory
		spawnManager = new CopSpawnManager(copNpcFactory, configProvider);

		// Rebuild the factory with the final spawn manager reference for behavior factory
		copNpcFactory = new CopNpcFactory(configProvider, entityMarkManager, spawnManager);

		// Reassign the factory in the spawn manager
		spawnManager = new CopSpawnManager(copNpcFactory, configProvider);

		copManager = new CopManager(plugin, spawnManager, targetingManager, configProvider, entityMarkManager);

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