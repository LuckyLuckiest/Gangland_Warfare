package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.bag.BagEnv;
import org.luckyraven.gangland.copsncrooks.bag.BagHud;
import org.luckyraven.gangland.copsncrooks.bag.BagService;
import org.luckyraven.gangland.copsncrooks.bag.BagSettings;
import org.luckyraven.gangland.copsncrooks.bag.BagSpill;
import org.luckyraven.gangland.copsncrooks.bag.HotCashBags;
import org.luckyraven.gangland.copsncrooks.database.HotCashBagRepository;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.function.Supplier;

/**
 * 0.20.0 lane bag: the hot-cash bag beans and the seams it installs (router, intake seizure, bribe funding, paperwork
 * line, HUD task, shutdown hook).
 */
@Configuration
public class BagModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public BagModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

	/** Live {@code Wanted.Bag}: re-reads the loader on every get. */
	static Supplier<BagSettings> settingsOf(ChaseConfigLoader loader) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The store: wires the repository data supplier and loads the saved rows. */
	@Bean
	public HotCashBags hotCashBags(HotCashBagRepository repository) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Bean
	public BagSpill bagSpill(JavaPlugin plugin, MoneyAddon moneyAddon, MoneyDepositService depositService,
	                         ChaseConfigLoader config) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Bean
	public BagEnv bagEnv(JavaPlugin plugin, @Qualifier("online") UserManager<Player> users,
	                     DetainmentService detainment, WantedStars stars, BagSpill spill, ChaseConfigLoader config,
	                     WantedMessages messages) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Bean
	public BagService bagService(HotCashBags bags, ChaseConfigLoader config, WantedMessages messages, BagEnv env) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Bean
	public BagHud bagHud(JavaPlugin plugin, BagService service, ChaseConfigLoader config) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Installs the router, intake seizure, bribe funding, paperwork line, shutdown hook and starts the HUD. */
	@PostConstruct
	public void installBag() {
		throw new UnsupportedOperationException("TDD stub");
	}
}
