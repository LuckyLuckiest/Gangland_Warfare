package org.luckyraven.gangland.fights;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * KERNEL-phase registration of the fights module's own YAML defaults ({@code fight/fight_rules.yml},
 * {@code fight/fight_messages.yml}, {@code fight/arenas.yml}); each is copied out of the module jar through the
 * module classloader. Same shape as {@code MailModuleFileConfig}.
 */
@Configuration(phase = Phase.KERNEL)
public final class FightsModuleFileConfig {

	private final JavaPlugin plugin;

	public FightsModuleFileConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Bean
	public FightsModuleFiles fightsModuleFiles(FileManager fileManager, ModuleLoader moduleLoader) {
		FileHandler rules = new FileHandler(plugin, "fight_rules", "fight", ".yml", moduleLoader.classLoader());
		fileManager.addFile(rules, true);

		FileHandler messages = new FileHandler(plugin, "fight_messages", "fight", ".yml", moduleLoader.classLoader());
		fileManager.addFile(messages, true);

		FileHandler arenas = new FileHandler(plugin, "arenas", "fight", ".yml", moduleLoader.classLoader());
		fileManager.addFile(arenas, true);

		return new FightsModuleFiles(rules, messages, arenas);
	}

}
