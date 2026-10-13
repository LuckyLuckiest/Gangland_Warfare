package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import java.util.function.ToIntFunction;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.perch.OverwatchController;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/** 0.20.0 lane overwatch: the Marksman overwatch controller bean and its AI-tick hook. */
@Configuration
public class OverwatchModuleConfig {

	private final DependencyContainer container;

	public OverwatchModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.container = container;
	}

	@Bean
	public OverwatchController overwatchController(CopLoader copLoader, CopManager copManager, CopRadio copRadio,
	                                               @Qualifier("online") UserManager<Player> users) {
		return new OverwatchController(copLoader::getLoadedProvider, copManager, copRadio, System::currentTimeMillis,
		                               levelOf(users));
	}

	/** Reads the wanted level; 0 when the player is unknown. */
	static ToIntFunction<Player> levelOf(UserManager<Player> users) {
		return p -> {
			User<Player> user = users.getUser(p);
			return user == null ? 0 : user.getWanted().getLevel();
		};
	}

	@PostConstruct
	public void installOverwatch() {
		OverwatchController controller = container.getInstance(OverwatchController.class);
		container.getInstance(CopManager.class).addAiTickHook(controller::tick);
	}
}
