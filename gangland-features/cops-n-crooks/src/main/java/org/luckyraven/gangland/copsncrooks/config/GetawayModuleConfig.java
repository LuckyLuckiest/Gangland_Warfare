package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.events.user.UserLevelUpEvent;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.time.ZoneId;

/**
 * 0.21.0 wave 2 (Getaway lane): wires the {@link GetawayService} bean.
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
		return new GetawayService(arcs, config, gangs, detainment, messages, Bukkit::getOnlinePlayers,
		                          (player, xp) -> grantXp(users, player, xp), ZoneId.systemDefault());
	}

	/** Adds {@code xp} to the player's level (UserLevelUpEvent, like a civilian kill); no user = no-op. */
	public static void grantXp(UserManager<Player> users, Player player, int xp) {
		User<Player> user = users.getUser(player);
		if (user == null) {
			return;
		}

		Level level = user.getLevel();
		level.addExperience(xp, new UserLevelUpEvent(false, user, level));
	}

}
