package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.place.SetupPointRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.bribe.BribeStars;
import org.luckyraven.gangland.copsncrooks.wanted.config.BribeStarSettings;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * 0.15.0 chase configuration: the {@code copsncrooks/wanted.yml} loader and the {@code copsncrooks/wanted_messages.yml} strings.
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

	@Bean
	public BribeStars bribeStars(SetupPointRegistry setupPoints, @Qualifier("online") UserManager<Player> userManager,
	                             ChaseConfigLoader config, WantedMessages messages, CopManager copManager) {
		return new BribeStars(plugin, setupPoints, userManager, () -> {
			BribeStarSettings bribe = config.get().bribeStars();
			return bribe == null ? BribeStarSettings.DEFAULT : bribe;
		}, () -> config.get().evasion().lostSightSeconds(), messages, copManager, System::currentTimeMillis);
	}
}
