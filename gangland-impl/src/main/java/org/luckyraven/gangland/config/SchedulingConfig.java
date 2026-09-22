package org.luckyraven.gangland.config;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.PeriodicalUpdates;
import org.luckyraven.gangland.bootstrap.PlayerBootstrapService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.gangland.data.plugin.PluginManager;
import org.luckyraven.gangland.data.user.UserDataLoader;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * CONFIG-phase wiring for the lifecycle service beans that automate plugin reload, player loading, and periodic
 * auto-save. Every bean here implements {@link BeanLifecycle} and participates in the unified
 * {@code context.reloadBeans()} / {@code context.shutdownBeans()} pipelines.
 *
 * <p>Topological ordering between these beans is enforced by declaring upstream services as constructor parameters,
 * even when the downstream bean doesn't invoke methods on them at runtime:
 * <ul>
 *     <li>{@link PlayerBootstrapService} depends on {@link FileManager} — files reload before players load</li>
 * </ul>
 */
@Configuration
public class SchedulingConfig {

	private final Gangland gangland;

	public SchedulingConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public PlayerBootstrapService playerBootstrapService(GanglandDatabase ganglandDatabase,
	                                                     @Qualifier("online") UserManager<Player> userManager,
	                                                     @Qualifier("offline")
														 UserManager<OfflinePlayer> offlineUserManager,
	                                                     MemberManager memberManager,
	                                                     UniqueItemAddon uniqueItemAddon,
	                                                     UserDataLoader userDataLoader,
	                                                     @SuppressWarnings("unused") FileManager fileManager) {
		return new PlayerBootstrapService(gangland, ganglandDatabase, userManager, offlineUserManager, memberManager,
		                                  userDataLoader, uniqueItemAddon);
	}

	@Bean
	public PeriodicalUpdates periodicalUpdates(GanglandDatabase ganglandDatabase,
	                                           PluginManager pluginManager,
	                                           @Qualifier("online") UserManager<Player> userManager,
	                                           @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                                           DependencyContainer container) {
		if (Settings.isAutoSave()) {
			long interval = Settings.getAutoSaveTime() * 60L;
			return new PeriodicalUpdates(gangland, ganglandDatabase, pluginManager, userManager,
			                             offlineUserManager, container, interval);
		}
		return new PeriodicalUpdates(gangland, ganglandDatabase, pluginManager, userManager,
		                             offlineUserManager, container);
	}
}
