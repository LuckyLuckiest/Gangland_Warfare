package me.luckyraven.config;

import me.luckyraven.Gangland;
import me.luckyraven.bootstrap.PeriodicalUpdates;
import me.luckyraven.bootstrap.PlayerBootstrapService;
import me.luckyraven.bootstrap.ScoreboardLifecycleService;
import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.BeanLifecycle;
import me.luckyraven.core.bean.Configuration;
import me.luckyraven.core.bean.Qualifier;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.plugin.PluginManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.weapon.WeaponManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * CONFIG-phase wiring for the four lifecycle service beans that automate plugin reload, player loading, scoreboard
 * creation, and periodic auto-save. Every bean here implements {@link BeanLifecycle} and participates in the unified
 * {@code context.reloadBeans()} / {@code context.shutdownBeans()} pipelines.
 *
 * <p>Topological ordering between these beans is enforced by declaring upstream services as constructor parameters,
 * even when the downstream bean doesn't invoke methods on them at runtime:
 * <ul>
 *     <li>{@link PlayerBootstrapService} depends on {@link FileManager} — files reload before players load</li>
 *     <li>{@link ScoreboardLifecycleService} depends on {@link PlayerBootstrapService} — players load before scoreboards
 *     are created</li>
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
	                                                     FileManager fileManager) {
		return new PlayerBootstrapService(gangland, ganglandDatabase, userManager, offlineUserManager, memberManager,
		                                  uniqueItemAddon, fileManager);
	}

	@Bean
	public ScoreboardLifecycleService scoreboardLifecycleService(ScoreboardManager scoreboardManager,
	                                                             @Qualifier("online") UserManager<Player> userManager,
	                                                             PlayerBootstrapService playerBootstrapService) {
		return new ScoreboardLifecycleService(gangland, scoreboardManager, userManager, playerBootstrapService);
	}

	@Bean
	public PeriodicalUpdates periodicalUpdates(GanglandDatabase ganglandDatabase,
	                                           PluginManager pluginManager,
	                                           @Qualifier("online") UserManager<Player> userManager,
	                                           @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                                           WeaponManager weaponManager) {
		if (Settings.isAutoSave()) {
			long interval = Settings.getAutoSaveTime() * 60L;
			return new PeriodicalUpdates(gangland, ganglandDatabase, pluginManager, userManager,
			                             offlineUserManager, weaponManager, interval);
		}
		return new PeriodicalUpdates(gangland, ganglandDatabase, pluginManager, userManager,
		                             offlineUserManager, weaponManager);
	}
}
