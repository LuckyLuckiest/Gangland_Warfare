package org.luckyraven.gangland.mail;

import lombok.CustomLog;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.mail.command.GangAllyMailContribution;
import org.luckyraven.gangland.mail.command.GangMailContribution;
import org.luckyraven.gangland.mail.contract.MailRepositoryContract;

/**
 * CONFIG-phase wiring for the mail module, registered by {@link MailModule} and folded into the host's bean pipeline
 * by {@code GanglandContext}. The {@link MailRepositoryContract} bean is dispensed off the {@link RepositoryRegistry}
 * (the {@code MailRepository} scanned from this module's {@code database} package implements it directly), which
 * mirrors the core's pattern for {@code GangAllianceRepositoryContract}.
 *
 * <p>{@link MailManager#initialize()} is NOT called here: the bean factory's convention pass runs it once, after the
 * phases, so the in-memory cache is hydrated and the data supplier is registered before LIFECYCLE beans wake up.
 * Calling it from the bean method as well loaded the table twice (T-194).
 *
 * <p>The two {@code CommandContribution} beans are how {@code /glw gang invite|accept|decline} and the alliance
 * request/accept/reject/pending flow reach the core's {@code GangCommand} without the core naming a mail type.
 *
 * <p>{@link MailExpirySweep} is the once-per-second main-thread expiry sweep, a {@code BeanLifecycle} that stops on
 * shutdown (GR-11).
 */
@CustomLog
@Configuration
public class MailModuleConfig {

	private final JavaPlugin plugin;

	public MailModuleConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Bean
	public MailRepositoryContract mailRepositoryContract(RepositoryRegistry registry) {
		return (MailRepositoryContract) registry.getRepository(MailItem.class);
	}

	@Bean
	public MailManager mailManager(MailRepositoryContract repository) {
		return new MailManager(repository);
	}

	@Bean
	public MailExpirySweep mailExpirySweep(JavaPlugin plugin, MailManager manager) {
		MailExpirySweep sweep = new MailExpirySweep(plugin, manager);
		sweep.onInitialize(true);
		return sweep;
	}

	@Bean
	public MailRules mailRules(MailModuleFiles files, FileManager fileManager) {
		MailRules rules = new MailRules(fileManager);
		fileManager.registerInitializer(rules);
		return rules;
	}

	@Bean
	public MailMessages mailMessages(MailModuleFiles files, FileManager fileManager) {
		MailMessages messages = new MailMessages(fileManager);
		fileManager.registerInitializer(messages);
		return messages;
	}

	@Bean
	public GangMailContribution gangMailContribution(@Qualifier("online") UserManager<Player> userManager,
	                                                 @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                                                 MemberManager memberManager,
	                                                 GangManager gangManager,
	                                                 RankManager rankManager,
	                                                 MailManager mailManager,
	                                                 MailRules mailRules,
	                                                 MailMessages mailMessages) {
		return new GangMailContribution(plugin, userManager, offlineUserManager, memberManager, gangManager,
		                                rankManager, mailManager, mailRules, mailMessages);
	}

	@Bean
	public GangAllyMailContribution gangAllyMailContribution(@Qualifier("online") UserManager<Player> userManager,
	                                                         MemberManager memberManager,
	                                                         GangManager gangManager,
	                                                         MailManager mailManager) {
		return new GangAllyMailContribution(plugin, userManager, memberManager, gangManager, mailManager);
	}

}
