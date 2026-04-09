package me.luckyraven.config;

import me.luckyraven.Gangland;
import me.luckyraven.PeriodicalUpdates;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.plugin.PluginManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.file.LanguageLoader;
import me.luckyraven.file.configuration.MoneyAddonInitializer;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.inventory.InventoryLoader;
import me.luckyraven.file.configuration.weapon.WeaponLoader;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.scoreboard.configuration.ScoreboardAddon;
import me.luckyraven.service.FileAddonReloadService;
import me.luckyraven.service.PlayerBootstrapService;
import me.luckyraven.service.ScoreboardLifecycleService;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.util.autowire.bean.Qualifier;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import me.luckyraven.weapon.configuration.WeaponAddon;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * CONFIG-phase wiring for the four lifecycle service beans that automate plugin reload, player loading, scoreboard
 * creation, and periodic auto-save. Every bean here implements {@link me.luckyraven.util.autowire.bean.BeanLifecycle}
 * and participates in the unified {@code context.reloadBeans()} / {@code context.shutdownBeans()} pipelines.
 *
 * <p>Topological ordering between these beans is enforced by declaring upstream services as constructor parameters,
 * even when the downstream bean doesn't invoke methods on them at runtime:
 * <ul>
 *     <li>{@link PlayerBootstrapService} depends on {@link FileAddonReloadService} — files reload before players load</li>
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
	public FileAddonReloadService fileAddonReloadService(FileManager fileManager,
	                                                     Settings settings,
	                                                     LanguageLoader languageLoader,
	                                                     ScoreboardAddon scoreboardAddon,
	                                                     AmmunitionManager ammunitionManager,
	                                                     AmmunitionAddon ammunitionAddon,
	                                                     WeaponAddon weaponAddon,
	                                                     WeaponLoader weaponLoader,
	                                                     UniqueItemAddon uniqueItemAddon,
	                                                     WearableAddon wearableAddon,
	                                                     CarAddon carAddon,
	                                                     MoneyAddon moneyAddon,
	                                                     MoneyAddonInitializer moneyAddonInitializer,
	                                                     InventoryLoader inventoryLoader) {
		return new FileAddonReloadService(fileManager, settings, languageLoader, scoreboardAddon,
		                                  ammunitionManager, ammunitionAddon, weaponAddon, weaponLoader,
		                                  uniqueItemAddon, wearableAddon, carAddon, moneyAddon,
		                                  moneyAddonInitializer, inventoryLoader);
	}

	@Bean
	public PlayerBootstrapService playerBootstrapService(GanglandDatabase ganglandDatabase,
	                                                     @Qualifier("online") UserManager<Player> userManager,
	                                                     @Qualifier("offline")
														 UserManager<OfflinePlayer> offlineUserManager,
	                                                     MemberManager memberManager,
	                                                     UniqueItemAddon uniqueItemAddon,
	                                                     FileAddonReloadService fileAddonReloadService) {
		return new PlayerBootstrapService(gangland, ganglandDatabase, userManager, offlineUserManager,
		                                  memberManager, uniqueItemAddon, fileAddonReloadService);
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
