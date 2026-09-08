package org.luckyraven.gangland.copsncrooks.config;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandTraderEconomy;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandTraderMessages;
import org.luckyraven.gangland.copsncrooks.integration.config.TraderSettingsImpl;
import org.luckyraven.gangland.copsncrooks.npc.trader.ShopViewOpener;
import org.luckyraven.gangland.copsncrooks.npc.trader.ShopViewOpenerImpl;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderData;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderManager;
import org.luckyraven.gangland.copsncrooks.npc.trader.config.TraderSettings;
import org.luckyraven.gangland.copsncrooks.npc.trader.economy.TraderEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.trader.message.TraderMessageContract;
import org.luckyraven.gangland.copsncrooks.npc.trader.mood.MoodService;
import org.luckyraven.gangland.copsncrooks.npc.trader.respawn.TraderRespawnService;
import org.luckyraven.gangland.copsncrooks.npc.trader.trait.TraderTraitRegistry;
import org.luckyraven.gangland.copsncrooks.npc.trader.trait.TraderTraitsLoader;
import org.luckyraven.gangland.copsncrooks.npc.trader.view.*;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.file.configuration.shop.GanglandShopDisplayResolver;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.gangland.shop.ShopRegistry;
import org.luckyraven.gangland.shop.message.ShopDisplayResolver;
import org.luckyraven.gangland.shop.message.ShopMessageContract;
import org.luckyraven.gangland.shop.valuation.CategoryBarterValuator;
import org.luckyraven.gangland.shop.valuation.SellValuator;
import org.luckyraven.gangland.shop.view.ShopAdminFlow;
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

	private final Gangland        gangland;
	private final GanglandContext context;

	public TraderModuleConfig(Gangland gangland, GanglandContext context) {
		this.gangland = gangland;
		this.context  = context;
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
		PermissionManager permissionManager = context.get(PermissionManager.class);
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
	public TraderSettings traderSettings(@SuppressWarnings("unused") Settings settings) {
		return new TraderSettingsImpl();
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
		return new BarterView(gangland, moodService, barterValuator, refresherRegistry, displayResolver,
		                      traderSettings);
	}

	@Bean
	public QuantitySelectorView quantitySelectorView() {
		return new QuantitySelectorView(gangland);
	}

	@Bean
	public NegotiationView negotiationView(MoodService moodService, TraderSettings traderSettings,
	                                       TraderMessageContract traderMessages, TraderEconomyContract economy,
	                                       ShopDisplayResolver displayResolver) {
		return new NegotiationView(gangland, moodService, traderSettings, traderMessages, economy, displayResolver);
	}

	@Bean
	public SellView traderSellView(MoodService moodService, SellValuator sellValuator,
	                               ItemRefresherRegistry refresherRegistry,
	                               TraderSettings traderSettings,
	                               ShopDisplayResolver displayResolver) {
		return new SellView(gangland, moodService, sellValuator, refresherRegistry,
		                    traderSettings, displayResolver);
	}

	@Bean
	public ShopView traderShopView(MoodService moodService, TraderSettings traderSettings,
	                               ShopDisplayResolver displayResolver) {
		return new ShopView(gangland, moodService, traderSettings, displayResolver);
	}

	@Bean
	public ModeSelectView traderModeSelectView(TraderSettings traderSettings) {
		return new ModeSelectView(gangland, traderSettings);
	}

	@Bean
	public TraderFlow traderFlow(ModeSelectView modeSelectView, ShopView shopView, NegotiationView negotiationView,
	                             SellView sellView, BarterView barterView, QuantitySelectorView quantityView) {
		return new TraderFlow(gangland, modeSelectView, shopView, negotiationView, sellView, barterView, quantityView);
	}

	// ── Trader NPC lifecycle ─────────────────────────────────────────────

	@Bean
	public TraderRespawnService traderRespawnService(TraderSettings settings) {
		return new TraderRespawnService(gangland, settings);
	}

	@Bean
	public TraderManager traderManager(TraderSettings settings, TraderTraitRegistry traitRegistry,
	                                   MoodService moodService, TraderRespawnService respawnService,
	                                   RepositoryRegistry repositoryRegistry) {
		IRepository<TraderData> repo = repositoryRegistry.getRepository(TraderData.class);
		return new TraderManager(gangland, repo, settings, traitRegistry, moodService, respawnService);
	}

	@Bean
	public ShopViewOpener shopViewOpener(TraderManager traderManager, ShopRegistry shopRegistry,
	                                     TraderFlow traderFlow, ShopAdminFlow adminFlow,
	                                     ShopMessageContract shopMessages,
	                                     TraderMessageContract traderMessages) {
		return new ShopViewOpenerImpl(traderManager, shopRegistry, traderFlow, adminFlow,
		                              shopMessages, traderMessages);
	}

}
