package org.luckyraven.gangland.gang;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;

/**
 * Lane W5-S beans: the gang stats service. Other modules' contributions (turfs, fight wins) are looked up from the
 * container on every refresh; {@code GangCommand} hands the service that lookup because the container is not a bean.
 */
@Configuration
public final class GangStatsConfig {

	@Bean
	public GangStatsService gangStatsService(JavaPlugin plugin, GangManager gangManager, GangRules rules,
	                                         @Qualifier("online") UserManager<Player> online,
	                                         @Qualifier("offline") UserManager<OfflinePlayer> offline) {
		return new GangStatsService(plugin, gangManager, rules, null,
		                            uuid -> wallet(online, offline, uuid),
		                            gangId -> onlineCount(gangManager, online, gangId));
	}

	/** A member's wallet: the online user first, then the offline one, {@code 0} when neither is loaded. */
	private static double wallet(UserManager<Player> online, UserManager<OfflinePlayer> offline,
	                             java.util.UUID uuid) {
		User<Player> user = online.getUser(uuid);
		if (user != null) return user.getEconomy().getAmount().doubleValue();

		User<OfflinePlayer> away = offline.getUser(uuid);
		return away == null ? 0D : away.getEconomy().getAmount().doubleValue();
	}

	private static int onlineCount(GangManager gangManager, UserManager<Player> online, int gangId) {
		Gang gang = gangManager.getGang(gangId);
		return gang == null ? 0 : gang.getOnlineMembers(online::getUser).size();
	}
}
