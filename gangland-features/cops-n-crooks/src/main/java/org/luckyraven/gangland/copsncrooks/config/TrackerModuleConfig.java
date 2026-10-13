package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.TrackerTags;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/** Beans of the tracker tags (0.20.0): the tag book and its install into the cop AI tick. */
@Configuration
public class TrackerModuleConfig {

	private final DependencyContainer container;

	public TrackerModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.container = container;
	}

	@Bean
	public TrackerTags trackerTags(CopLoader copLoader, @Qualifier("online") UserManager<Player> users, CopRadio copRadio,
	                               WantedMessages messages, JavaPlugin plugin) {
		return null;
	}

	@PostConstruct
	public void installTracker() {
		throw new UnsupportedOperationException("TDD stub");
	}
}
