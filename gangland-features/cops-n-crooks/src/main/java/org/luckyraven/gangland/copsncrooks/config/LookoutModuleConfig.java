package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.LookoutRelay;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/** 0.20.0 lane look: the lookout relay predicate, installed on the cop radio. */
@Configuration
public class LookoutModuleConfig {

	private final DependencyContainer container;

	public LookoutModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.container = container;
	}

	@Bean
	public LookoutRelay lookoutRelay(CopLoader copLoader, @Qualifier("online") UserManager<Player> users,
	                                 GangMembership gangMembership) {
		// a missing provider (cops.yml not loaded yet) keeps the lookout on, like the shipped default
		return new LookoutRelay(() -> {
			CopConfigProvider provider = copLoader.getLoadedProvider();
			return provider == null || provider.isLookoutEnabled();
		}, DisguiseModuleConfig.levelOf(users), gangMembership);
	}

	@PostConstruct
	public void installLookout() {
		container.getInstance(CopRadio.class).setLookout(container.getInstance(LookoutRelay.class));
	}
}
