package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * 0.15.0 chase configuration: the {@code npc/wanted.yml} loader and the {@code npc/wanted_messages.yml} strings.
 */
@Configuration
public class ChaseModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public ChaseModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

	@Bean
	public ChaseConfigLoader chaseConfigLoader(FileManager fileManager) {
		ChaseConfigLoader loader = new ChaseConfigLoader(plugin, fileManager);
		fileManager.registerInitializer(loader);
		fileManager.initializeAll();
		return loader;
	}

	@Bean
	public WantedMessages wantedMessages(FileManager fileManager) {
		WantedMessages messages = new WantedMessages(fileManager);
		fileManager.registerInitializer(messages);
		return messages;
	}
}
