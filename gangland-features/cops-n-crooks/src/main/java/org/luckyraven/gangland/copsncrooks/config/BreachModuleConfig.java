package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.breach.BreachController;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/**
 * 0.21.0 breach: the stack-up and breach controller bean and its AI tick hook.
 */
@Configuration
public class BreachModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public BreachModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

	@Bean
	public BreachController breachController(CopLoader copLoader, CopRadio copRadio) {
		return new BreachController(copLoader::getLoadedProvider, copRadio, System::currentTimeMillis);
	}

	/** Feeds the controller from the cop AI tick and releases its stacks on reload/disable. */
	@PostConstruct
	public void installBreach() {
		CopManager       manager    = container.getInstance(CopManager.class);
		BreachController controller = container.getInstance(BreachController.class);
		manager.addAiTickHook(controller::tick);
		manager.addShutdownHook(controller::endAll);
	}

}
