package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.bootstrap.GanglandContext;
import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.Configuration;
import me.luckyraven.core.bean.PostConstruct;
import me.luckyraven.core.timer.RepeatingTimer;
import me.luckyraven.mail.MailItem;
import me.luckyraven.mail.MailManager;
import me.luckyraven.mail.contract.MailRepositoryContract;
import me.luckyraven.persistence.repository.RepositoryRegistry;

/**
 * CONFIG-phase wiring for the mail subsystem. The {@link MailRepositoryContract} bean is dispensed off the
 * {@link RepositoryRegistry} (the auto-scanned {@code MailRepository} implements it directly), which mirrors the
 * pattern in {@link GangModuleConfig} for {@code GangAllianceRepositoryContract}.
 *
 * <p>{@link MailManager#initialize()} runs in the bean factory so the in-memory cache is hydrated and the data
 * supplier is registered before LIFECYCLE beans wake up — without the supplier the periodic autosave would throw
 * {@code "No data supplier set for repository: MailRepository"} on the next tick.
 *
 * <p>The post-construct hook starts a once-per-second expiry sweep on the main thread; it's cheap when the cache is
 * small and removes the need for downstream code to ever look at {@link MailItem#isExpired()} inside a hot path.
 */
@CustomLog
@Configuration
public class MailConfig {

	private static final long EXPIRY_TICK_INTERVAL = 20L; // 1 second

	private final Gangland        gangland;
	private final GanglandContext context;

	private RepeatingTimer expiryTimer;

	public MailConfig(Gangland gangland, GanglandContext context) {
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

	@PostConstruct
	public void startExpirySweep() {
		MailManager manager = context.get(MailManager.class);

		expiryTimer = new RepeatingTimer(gangland, EXPIRY_TICK_INTERVAL, timer -> manager.expireDue());
		expiryTimer.start(true);
	}
}
