package org.luckyraven.gangland.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.membership.GangLeadership;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.membership.MembershipQueries;
import org.luckyraven.gangland.gang.purge.GangPurgeTask;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/** Joining, leaving, kicking, disbanding, leadership and the purge. */
@Configuration
public final class GangMembershipConfig {

	@Bean
	public MembershipQueries membershipQueries(GangManager gangManager) {
		return new MembershipQueries(gangManager);
	}

	@Bean
	public GangDisbandService gangDisbandService(@Qualifier("online") UserManager<Player> userManager,
	                                             @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                                             MemberManager memberManager, GangManager gangManager,
	                                             RepositoryRegistry repositoryRegistry, GangMessages messages) {
		return new GangDisbandService(userManager, offlineUserManager, memberManager, gangManager,
		                              repositoryRegistry, messages);
	}

	@Bean
	public GangLeadership gangLeadership(MemberManager memberManager, RankManager rankManager,
	                                     @Qualifier("online") UserManager<Player> userManager) {
		return new GangLeadership(memberManager, rankManager, userManager);
	}

	@Bean
	public GangMembershipService gangMembershipService(GangManager gangManager, MemberManager memberManager,
	                                                   RankManager rankManager,
	                                                   @Qualifier("online") UserManager<Player> userManager,
	                                                   @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                                                   GangLimits limits, CombatTag combatTag, GangRules rules,
	                                                   GangMessages messages, GangDisbandService disbandService,
	                                                   GangLeadership leadership) {
		return new GangMembershipService(gangManager, memberManager, rankManager, userManager, offlineUserManager,
		                                 limits, combatTag, rules, messages, disbandService, leadership,
		                                 line -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line));
	}

	@Bean
	public GangPurgeTask gangPurgeTask(JavaPlugin plugin, GangManager gangManager, MembershipQueries queries,
	                                   GangRules rules, GangDisbandService disbandService,
	                                   GangMembershipService membershipService) {
		return new GangPurgeTask(plugin, gangManager, queries, rules, disbandService, membershipService,
		                         GangPurgeTask.serverLastPlayed(), System::currentTimeMillis);
	}
}
