package org.luckyraven.gangland.turf;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.turf.npc.config.TurfNpcsConfigLoader;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * KERNEL-phase registration of the turf module's own YAML defaults. The FileManager is a KERNEL bean
 * (KernelConfig#fileManager), so BeanGraph orders this method after it; addFile(handler, true) creates the file
 * immediately (FileManager#addFile), long before PowerupRegistryLoader's CONFIG-phase constructor calls
 * checkFileLoaded("turf_powerups"). The five-argument FileHandler reads the bundled default out of the module
 * jar through the module classloader instead of the plugin jar.
 *
 * <p>{@code turf/turf_npcs.yml} joined this KERNEL-phase registration in group I (moved here, verbatim contents,
 * from cops-n-crooks' CopsNCrooksYamlConfig) — the registration must run before {@code FileManager} is used, and a
 * later-phase registration would race the CONFIG-phase code ({@link TurfNpcsConfigLoader}) that reads it, same
 * reasoning as {@code turf_powerups}.
 */
@Configuration(phase = Phase.KERNEL)
public final class TurfModuleFileConfig {

	private final JavaPlugin plugin;

	public TurfModuleFileConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Bean
	public TurfModuleFiles turfModuleFiles(FileManager fileManager, ModuleLoader moduleLoader) {
		FileHandler powerups = new FileHandler(plugin, "turf_powerups", "turf", ".yml",
		                                      moduleLoader.classLoader());
		fileManager.addFile(powerups, true);

		FileHandler npcs = new FileHandler(plugin, "turf_npcs", "turf", ".yml",
		                                  moduleLoader.classLoader());
		fileManager.addFile(npcs, true);

		return new TurfModuleFiles(powerups, npcs);
	}
}
