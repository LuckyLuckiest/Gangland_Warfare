package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.rescue.RescueService;
import org.luckyraven.gangland.copsncrooks.integration.detainment.DetainmentSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/**
 * 0.21.0 rescues: wires the {@link RescueService}.
 */
@Configuration
public class RescueModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public RescueModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

	/** Reads {@code Detainment.Rescue} live on every click, so a settings reload takes effect without a restart. */
	@Bean
	public RescueService rescueService(DetainmentSettings settings, CopManager copManager, DetainmentService detainment,
	                                   CuffLockRegistry cuffLockRegistry, ReleasePipeline releasePipeline,
	                                   GangMembership gangs, WantedStars wantedStars,
	                                   @Qualifier("online") UserManager<Player> users, WantedMessages messages) {
		return new RescueService(settings::getRescue, copManager, detainment, cuffLockRegistry, releasePipeline,
		                         gangs, wantedStars, users, messages, plugin, Bukkit::getPlayer,
		                         System::currentTimeMillis);
	}

}
