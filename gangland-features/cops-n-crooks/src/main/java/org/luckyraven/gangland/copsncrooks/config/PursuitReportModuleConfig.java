package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/**
 * 0.21.0 fnd-squad: empty feature configuration; the owning lane adds its {@code @Bean} methods here.
 */
@Configuration
public class PursuitReportModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public PursuitReportModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

}
