package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.report.BreakingNews;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/**
 * 0.21.0 pursuit report: the {@link BreakingNews} bean and its AI-tick hook for the time trigger.
 */
@Configuration
public class PursuitReportModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public PursuitReportModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

	@Bean
	public BreakingNews breakingNews(ChaseArcs arcs, ChaseConfigLoader config, WantedMessages messages) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@PostConstruct
	public void installNews() {
		throw new UnsupportedOperationException("TDD stub");
	}
}
