package org.luckyraven.gangland.healthbars.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * FILE-phase wiring for the health-bar module: registers {@code healthbars/healthbars.yml} from the module's own jar
 * and the {@link HealthBarSettings} that reads it (same shape as the gadget module's {@code GadgetFileConfig}).
 */
@Configuration(phase = Phase.FILE)
public class HealthBarsFileConfig {

	private final JavaPlugin plugin;

	public HealthBarsFileConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Bean
	public HealthBarSettings healthBarSettings(FileManager fileManager, ModuleLoader moduleLoader) {
		fileManager.addFile(new FileHandler(plugin, HealthBarSettings.FILE_NAME, "healthbars", ".yml",
		                                    moduleLoader.classLoader()), true);

		HealthBarSettings settings = new HealthBarSettings(fileManager);
		fileManager.registerInitializer(settings);
		return settings;
	}
}
