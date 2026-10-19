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
import org.luckyraven.gangland.copsncrooks.bag.ServerBagEnv;
import org.luckyraven.gangland.copsncrooks.detainment.bribe.BribeService;
import org.luckyraven.gangland.copsncrooks.detainment.intake.JailIntakeService;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.PaperworkView;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.data.economy.HotCashRouter;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyItem;
import org.luckyraven.gangland.item.money.MoneyItemFactory;
import org.luckyraven.gangland.item.money.MoneyItemUtil;
import org.luckyraven.keystone.util.ActionBarManager;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.Map;
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
		return loader::getBag;
	}

	/** The store: wires the repository data supplier and loads the saved rows. */
	@Bean
	public HotCashBags hotCashBags(HotCashBagRepository repository) {
		HotCashBags bags = new HotCashBags(repository);
		repository.setDataSupplier(bags::all);
		bags.load();
		return bags;
	}

	@Bean
	public BagSpill bagSpill(JavaPlugin plugin, MoneyAddon moneyAddon, MoneyDepositService depositService,
	                         ChaseConfigLoader config) {
		return new BagSpill(plugin, amount -> {
			String    id      = config.getBag().spillVariation();
			MoneyItem wanted  = moneyAddon.getVariation(id);
			MoneyItem picked  = BagSpill.pickVariation(id, wanted == null ? defaults(moneyAddon) : Map.of(id, wanted));
			return MoneyItemFactory.build(picked, amount, depositService);
		});
	}

	private static Map<String, MoneyItem> defaults(MoneyAddon moneyAddon) {
		MoneyItem fallback = moneyAddon.getDefaultVariation();
		return fallback == null ? Map.of() : Map.of("default", fallback);
	}

	@Bean
	public BagEnv bagEnv(JavaPlugin plugin, @Qualifier("online") UserManager<Player> users,
	                     DetainmentService detainment, WantedStars stars, BagSpill spill, ChaseConfigLoader config,
	                     WantedMessages messages) {
		return new ServerBagEnv(plugin, users, detainment, stars, spill, settingsOf(config), messages,
		                        MoneyItemUtil::isMoneyItem, MoneyItemUtil::readAmount,
		                        (player, text) -> ActionBarManager.send(plugin, player, text, 40L));
	}

	@Bean
	public BagService bagService(HotCashBags bags, ChaseConfigLoader config, WantedMessages messages, BagEnv env) {
		return new BagService(bags, settingsOf(config), messages, env);
	}

	@Bean
	public BagHud bagHud(JavaPlugin plugin, BagService service, ChaseConfigLoader config) {
		return new BagHud(plugin, service, settingsOf(config));
	}

	/** Installs the router, intake seizure, bribe funding, paperwork line, shutdown hook and starts the HUD. */
	@PostConstruct
	public void installBag() {
		container.getInstance(HotCashRouter.class).install(container.getInstance(BagService.class));
		BagService service = container.getInstance(BagService.class);
		BagHud     hud     = container.getInstance(BagHud.class);
		container.getInstance(JailIntakeService.class).setBagSeizure(service::seize);
		container.getInstance(BribeService.class).setBagFunding(service);
		container.getInstance(PaperworkView.class).setBagLine(service::paperworkLine);
		hud.start();
		container.getInstance(CopManager.class).addShutdownHook(() -> {
			service.shutdown();
			hud.stop();
		});
	}
}
