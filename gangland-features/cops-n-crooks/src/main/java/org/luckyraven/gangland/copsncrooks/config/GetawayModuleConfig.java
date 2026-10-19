package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

/**
 * 0.21.0 wave 2 (Getaway lane): empty configuration shell; the lane adds its beans here.
 */
@Configuration
public class GetawayModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public GetawayModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

	@Bean
	public GetawayService getawayService(ChaseArcs arcs, ChaseConfigLoader config, GangMembership gangs,
	                                     DetainmentService detainment, WantedMessages messages,
	                                     @Qualifier("online") UserManager<Player> users) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Adds {@code xp} to the player's level (UserLevelUpEvent, like a civilian kill); no user = no-op. */
	public static void grantXp(UserManager<Player> users, Player player, int xp) {
		throw new UnsupportedOperationException("TDD stub");
	}

}
