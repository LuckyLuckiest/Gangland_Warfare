package org.luckyraven.gangland.turf;

import org.luckyraven.gangland.Gangland;
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
 */
@Configuration(phase = Phase.KERNEL)
public final class TurfModuleFileConfig {

	private final Gangland gangland;

	public TurfModuleFileConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public TurfModuleFiles turfModuleFiles(FileManager fileManager, ModuleLoader moduleLoader) {
		FileHandler powerups = new FileHandler(gangland, "turf_powerups", "turf", ".yml",
		                                      moduleLoader.classLoader());
		fileManager.addFile(powerups, true);
		return new TurfModuleFiles(powerups);
	}
}
