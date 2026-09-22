package org.luckyraven.gangland.npcshops.config;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.gangland.npcshops.integration.GanglandTraderEconomy;
import org.luckyraven.gangland.npcshops.integration.GanglandTraderMessages;
import org.luckyraven.gangland.npcshops.integration.TraderSettingsImpl;
import org.luckyraven.gangland.npcshops.trader.ShopViewOpener;
import org.luckyraven.gangland.npcshops.trader.ShopViewOpenerImpl;
import org.luckyraven.gangland.npcshops.trader.TraderData;
import org.luckyraven.gangland.npcshops.trader.TraderManager;
import org.luckyraven.gangland.npcshops.trader.config.TraderSettings;
import org.luckyraven.gangland.npcshops.trader.economy.TraderEconomyContract;
import org.luckyraven.gangland.npcshops.trader.message.TraderMessageContract;
import org.luckyraven.gangland.npcshops.trader.mood.MoodService;
import org.luckyraven.gangland.npcshops.trader.respawn.TraderRespawnService;
import org.luckyraven.gangland.npcshops.trader.trait.TraderTraitRegistry;
import org.luckyraven.gangland.npcshops.trader.trait.TraderTraitsLoader;
import org.luckyraven.gangland.npcshops.trader.view.*;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.shop.ShopRegistry;
import org.luckyraven.keystone.shop.message.ShopDisplayResolver;
import org.luckyraven.keystone.shop.message.ShopMessageContract;
import org.luckyraven.keystone.shop.valuation.CategoryBarterValuator;
import org.luckyraven.keystone.shop.valuation.SellValuator;
import org.luckyraven.gangland.shop.ShopAdminOpener;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/**
 * Bean wiring for the Trader NPC feature. Moved from the core {@code ShopConfig} (T15, module split sprint
 * 2026-09-07); the shop-api beans ({@code ShopRegistry}, purchase/barter/sell services, valuators, admin views,
 * {@code shopAdminFlow}, …) stay in core so {@code /glw shop} works with zero modules installed.
 */
@CustomLog
@Configuration
public class TraderModuleConfig {

	private final JavaPlugin        plugin;
	private final DependencyContainer container;

	public TraderModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin = plugin;
		this.container  = container;
	}

	/**
	 * Registers the {@code gangland.shop.admin} node (the gate for sneak-opening {@code ShopAdminView}) with the
	 * {@link PermissionManager} so it appears in {@code /glw perm shop} and is selectable in rank permission
	 * autocompletion. {@link PermissionManager} is resolved lazily from the context because it's registered by
	 * another CONFIG-phase {@code @Configuration}; taking it as a constructor arg would trigger the same ordering
	 * failure that caught {@code GangFilterRegistration}.
	 */
	@PostConstruct
	public void registerPermissions() {
		PermissionManager permissionManager = container.getInstance(PermissionManager.class);
		if (permissionManager != null) permissionManager.addPermission(ShopViewOpenerImpl.ADMIN_PERMISSION);
	}

	@Bean
	public MoodService moodService() {
		return new MoodService();
	}

	@Bean
	public TraderMessageContract traderMessageContract() {
		return new GanglandTraderMessages();
	}

	// ── Traits (loaded from plugin/trader_traits.yml) ────────────────────

	@Bean
	public TraderTraitRegistry traderTraitRegistry() {
		return new TraderTraitRegistry();
	}

	@Bean
	public TraderTraitsLoader traderTraitsLoader(TraderTraitRegistry registry, FileManager fileManager) {
		return new TraderTraitsLoader(registry, fileManager);
	}

	// ── Settings ─────────────────────────────────────────────────────────

	@Bean
	public TraderSettings traderSettings(@SuppressWarnings("unused") Settings settings, FileManager fileManager) {
		return new TraderSettingsImpl(fileManager);
	}

	// ── Views ────────────────────────────────────────────────────────────

	@Bean
	public TraderEconomyContract traderEconomyContract(@Qualifier("online") UserManager<Player> userManager) {
		return new GanglandTraderEconomy(userManager);
	}

	@Bean
	public BarterView barterView(MoodService moodService, CategoryBarterValuator barterValuator,
	                             ItemRefresherRegistry refresherRegistry,
	                             ShopDisplayResolver displayResolver,
	                             TraderSettings traderSettings) {
		return new BarterView(plugin, moodService, barterValuator, refresherRegistry, displayResolver,
		                      traderSettings);
	}

	@Bean
	public QuantitySelectorView quantitySelectorView() {
		return new QuantitySelectorView(plugin);
	}

	@Bean
	public NegotiationView negotiationView(MoodService moodService, TraderSettings traderSettings,
	                                       TraderMessageContract traderMessages, TraderEconomyContract economy,
	                                       ShopDisplayResolver displayResolver) {
		return new NegotiationView(plugin, moodService, traderSettings, traderMessages, economy, displayResolver);
	}

	@Bean
	public SellView traderSellView(MoodService moodService, SellValuator sellValuator,
	                               ItemRefresherRegistry refresherRegistry,
	                               TraderSettings traderSettings,
	                               ShopDisplayResolver displayResolver) {
		return new SellView(plugin, moodService, sellValuator, refresherRegistry,
		                    traderSettings, displayResolver);
	}

	@Bean
	public ShopView traderShopView(MoodService moodService, TraderSettings traderSettings,
	                               ShopDisplayResolver displayResolver) {
		return new ShopView(plugin, moodService, traderSettings, displayResolver);
	}

	@Bean
	public ModeSelectView traderModeSelectView(TraderSettings traderSettings) {
		return new ModeSelectView(plugin, traderSettings);
	}

	@Bean
	public TraderFlow traderFlow(InventoryService inventoryService, ModeSelectView modeSelectView, ShopView shopView,
	                             NegotiationView negotiationView, SellView sellView, BarterView barterView,
	                             QuantitySelectorView quantityView) {
		return new TraderFlow(plugin, inventoryService, modeSelectView, shopView, negotiationView, sellView,
		                      barterView, quantityView);
	}

	// ── Trader NPC lifecycle ─────────────────────────────────────────────

	@Bean
	public TraderRespawnService traderRespawnService(TraderSettings settings) {
		return new TraderRespawnService(plugin, settings);
	}

	@Bean
	public TraderManager traderManager(TraderSettings settings, TraderTraitRegistry traitRegistry,
	                                   MoodService moodService, TraderRespawnService respawnService,
	                                   RepositoryRegistry repositoryRegistry) {
		IRepository<TraderData> repo = repositoryRegistry.getRepository(TraderData.class);
		return new TraderManager(plugin, repo, settings, traitRegistry, moodService, respawnService);
	}

	@Bean
	public ShopViewOpener shopViewOpener(TraderManager traderManager, ShopRegistry shopRegistry,
	                                     TraderFlow traderFlow, ShopAdminOpener adminFlow,
	                                     ShopMessageContract shopMessages,
	                                     TraderMessageContract traderMessages) {
		return new ShopViewOpenerImpl(traderManager, shopRegistry, traderFlow, adminFlow,
		                              shopMessages, traderMessages);
	}

}
