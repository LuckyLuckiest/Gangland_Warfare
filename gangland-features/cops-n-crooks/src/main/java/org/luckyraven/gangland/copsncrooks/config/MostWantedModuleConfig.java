package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/**
 * 0.21.0 wave 2 (Most Wanted lane): empty configuration shell; the lane adds its beans here.
 */
@Configuration
public class MostWantedModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public MostWantedModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

}
