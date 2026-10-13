package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.surrender.SurrenderService;
import org.luckyraven.gangland.copsncrooks.integration.detainment.DetainmentSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/**
 * 0.21.0 Surrender: the {@link SurrenderService} bean and its hooks into the cop manager.
 */
@Configuration
public class SurrenderModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public SurrenderModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

	@Bean
	public SurrenderService surrenderService(DetainmentSettings settings, CopRadio copRadio, WantedMessages messages,
	                                         CrimeService crimes, DetainmentService detainment) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@PostConstruct
	public void registerHooks() {
		throw new UnsupportedOperationException("TDD stub");
	}
}
