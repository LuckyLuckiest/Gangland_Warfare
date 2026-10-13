package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.item.CopsItems;
import org.luckyraven.gangland.copsncrooks.npc.police.SirenService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.ScannerOverhearing;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/** 0.20.0 lane scanner: the scanner overhearing predicate and the siren service, installed on the radio and the AI tick. */
@Configuration
public class ScannerModuleConfig {

	private final DependencyContainer container;

	public ScannerModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.container = container;
	}

	@Bean
	public ScannerOverhearing scannerOverhearing(CopLoader copLoader, CopsItems copsItems) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Bean
	public SirenService sirenService(CopLoader copLoader) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@PostConstruct
	public void installScanner() {
		throw new UnsupportedOperationException("TDD stub");
	}
}
