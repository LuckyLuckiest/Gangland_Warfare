package org.luckyraven.gangland.gang;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.safehouse.Safehouse;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/** Lane W5-H beans: safehouses and rallies. */
@Configuration
public final class GangSafehouseConfig {

	@Bean
	public GangSafehouseService gangSafehouseService(JavaPlugin plugin, RepositoryRegistry registry,
	                                                 @Qualifier("online") UserManager<Player> users,
	                                                 MemberManager members, GangManager gangs, GangRules rules,
	                                                 GangMessages messages, GangWorldPolicy worldPolicy) {
		IRepository<Safehouse> repository = registry.getRepository(Safehouse.class);
		return new GangSafehouseService(plugin, repository, users, members, gangs, rules, messages, worldPolicy);
	}
}
