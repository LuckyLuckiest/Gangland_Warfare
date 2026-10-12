package org.luckyraven.gangland.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * KERNEL-phase registration of the gang module's own YAML defaults ({@code gang/gang_rules.yml} and
 * {@code gang/gang_messages.yml}, 0.17.0). The five-argument {@link FileHandler} copies each bundled default out of the
 * module jar through the module classloader. Same shape as {@code MailModuleFileConfig}; {@code gang/gang_settings.yml}
 * stays registered by {@code GangConfig.gangSettingsContract}.
 */
@Configuration(phase = Phase.KERNEL)
public final class GangModuleFileConfig {

	private final JavaPlugin plugin;

	public GangModuleFileConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Bean
	public GangModuleFiles gangModuleFiles(FileManager fileManager, ModuleLoader moduleLoader) {
		FileHandler rules = new FileHandler(plugin, "gang_rules", "gang", ".yml", moduleLoader.classLoader());
		fileManager.addFile(rules, true);

		FileHandler messages = new FileHandler(plugin, "gang_messages", "gang", ".yml", moduleLoader.classLoader());
		fileManager.addFile(messages, true);

		return new GangModuleFiles(rules, messages);
	}

}
