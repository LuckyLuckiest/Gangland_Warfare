package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.item.CopsItems;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.SirenService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SirenSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
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

	/** The loaded provider is fetched on every call, so a config reload takes effect without rebuilding the bean. */
	@Bean
	public ScannerOverhearing scannerOverhearing(CopLoader copLoader, CopsItems copsItems) {
		return new ScannerOverhearing(() -> {
			CopConfigProvider provider = copLoader.getLoadedProvider();
			return provider == null ? ScannerSettings.DEFAULT : provider.getScannerSettings();
		}, copsItems::isScanner);
	}

	@Bean
	public SirenService sirenService(CopLoader copLoader) {
		return new SirenService(() -> {
			CopConfigProvider provider = copLoader.getLoadedProvider();
			return provider == null ? SirenSettings.DEFAULT : provider.getSirenSettings();
		});
	}

	@PostConstruct
	public void installScanner() {
		SirenService siren = container.getInstance(SirenService.class);
		container.getInstance(CopRadio.class).setOverhearing(container.getInstance(ScannerOverhearing.class));
		container.getInstance(CopManager.class).addAiTickHook((Player p, CopGroup g) -> siren.tick(p, g));
	}
}
