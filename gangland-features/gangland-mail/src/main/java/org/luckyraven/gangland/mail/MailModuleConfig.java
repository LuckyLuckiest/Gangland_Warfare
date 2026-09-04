package org.luckyraven.gangland.mail;

import lombok.CustomLog;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.mail.command.GangAllyMailContribution;
import org.luckyraven.gangland.mail.command.GangMailContribution;
import org.luckyraven.gangland.mail.contract.MailRepositoryContract;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.keystone.timer.RepeatingTimer;

/**
 * CONFIG-phase wiring for the mail module, registered by {@link MailModule} and folded into the host's bean pipeline
 * by {@code GanglandContext}. The {@link MailRepositoryContract} bean is dispensed off the {@link RepositoryRegistry}
 * (the {@code MailRepository} scanned from this module's {@code database} package implements it directly), which
 * mirrors the core's pattern for {@code GangAllianceRepositoryContract}.
 *
 * <p>{@link MailManager#initialize()} runs in the bean factory so the in-memory cache is hydrated and the data
 * supplier is registered before LIFECYCLE beans wake up — without the supplier the periodic autosave would throw
 * {@code "No data supplier set for repository: MailRepository"} on the next tick.
 *
 * <p>The two {@code CommandContribution} beans are how {@code /glw gang invite|accept} and the alliance
 * request/accept/reject/pending flow reach the core's {@code GangCommand} without the core naming a mail type.
 *
 * <p>The post-construct hook starts a once-per-second expiry sweep; it's cheap when the cache is small and removes the
 * need for downstream code to ever look at {@link MailItem#isExpired()} inside a hot path.
 */
@CustomLog
@Configuration
public class MailModuleConfig {

	private static final long EXPIRY_TICK_INTERVAL = 20L; // 1 second

	private final Gangland        gangland;
	private final GanglandContext context;

	private RepeatingTimer expiryTimer;

	public MailModuleConfig(Gangland gangland, GanglandContext context) {
		this.gangland = gangland;
		this.context  = context;
	}

	@Bean
	public MailRepositoryContract mailRepositoryContract(RepositoryRegistry registry) {
		return (MailRepositoryContract) registry.getRepository(MailItem.class);
	}

	@Bean
	public MailManager mailManager(MailRepositoryContract repository) {
		MailManager manager = new MailManager(repository);
		manager.initialize();
		return manager;
	}

	@Bean
	public GangMailContribution gangMailContribution(@Qualifier("online") UserManager<Player> userManager,
	                                                 @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                                                 MemberManager memberManager,
	                                                 GangManager gangManager,
	                                                 RankManager rankManager,
	                                                 MailManager mailManager) {
		return new GangMailContribution(gangland, userManager, offlineUserManager, memberManager, gangManager,
		                                rankManager, mailManager);
	}

	@Bean
	public GangAllyMailContribution gangAllyMailContribution(@Qualifier("online") UserManager<Player> userManager,
	                                                         MemberManager memberManager,
	                                                         GangManager gangManager,
	                                                         MailManager mailManager) {
		return new GangAllyMailContribution(gangland, userManager, memberManager, gangManager, mailManager);
	}

	@PostConstruct
	public void startExpirySweep() {
		MailManager manager = context.get(MailManager.class);

		expiryTimer = new RepeatingTimer(gangland, EXPIRY_TICK_INTERVAL, timer -> manager.expireDue());
		expiryTimer.start(true);
	}
}
