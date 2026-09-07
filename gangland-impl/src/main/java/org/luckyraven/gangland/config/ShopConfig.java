package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
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
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.file.configuration.copsncrooks.GanglandTraderEconomy;
import org.luckyraven.gangland.file.configuration.copsncrooks.GanglandTraderMessages;
import org.luckyraven.gangland.file.configuration.shop.GanglandShopDisplayResolver;
import org.luckyraven.gangland.file.configuration.shop.GanglandShopMessages;
import org.luckyraven.gangland.file.configuration.shop.TraderSettingsImpl;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.ItemRefresherRegistry;
import org.luckyraven.gangland.item.ItemSerializerRegistry;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.gangland.shop.ShopRegistry;
import org.luckyraven.gangland.shop.io.ShopYamlReader;
import org.luckyraven.gangland.shop.io.ShopYamlWriter;
import org.luckyraven.gangland.shop.message.ShopDisplayResolver;
import org.luckyraven.gangland.shop.message.ShopMessageContract;
import org.luckyraven.gangland.shop.transaction.ShopBarterService;
import org.luckyraven.gangland.shop.transaction.ShopPurchaseService;
import org.luckyraven.gangland.shop.transaction.ShopSellService;
import org.luckyraven.gangland.shop.valuation.CategoryBarterValuator;
import org.luckyraven.gangland.shop.valuation.CategorySellValuator;
import org.luckyraven.gangland.shop.valuation.SellValuator;
import org.luckyraven.gangland.shop.view.*;
import org.luckyraven.gangland.weapon.WeaponService;

@CustomLog
@Configuration
public class ShopConfig {

	private final Gangland        gangland;
	private final GanglandContext context;

	public ShopConfig(Gangland gangland, GanglandContext context) {
		this.gangland = gangland;
		this.context  = context;
	}

	/**
	 * Registers the {@code gangland.shop.admin} node (the gate for sneak-opening {@code ShopAdminView}) with the
	 * {@link PermissionManager} so it appears in {@code /glw perm shop} and is selectable in rank permission
	 * autocompletion. {@link PermissionManager} is resolved lazily from the context because it's registered by another
	 * CONFIG-phase {@code @Configuration}; taking it as a constructor arg would trigger the same ordering failure that
	 * caught {@code GangFilterRegistration}.
	 */
	@PostConstruct
	public void registerPermissions() {
		PermissionManager permissionManager = context.get(PermissionManager.class);
		if (permissionManager != null) permissionManager.addPermission(ShopViewOpenerImpl.ADMIN_PERMISSION);
	}

	// ── YAML I/O + shared services ───────────────────────────────────────

	@Bean
	public ShopYamlReader shopYamlReader() {
		return new ShopYamlReader();
	}

	@Bean
	public ShopYamlWriter shopYamlWriter() {
		return new ShopYamlWriter();
	}

	@Bean
	public MoodService moodService() {
		return new MoodService();
	}

	// ── Message contracts ────────────────────────────────────────────────

	@Bean
	public ShopMessageContract shopMessageContract() {
		return new GanglandShopMessages();
	}

	@Bean
	public TraderMessageContract traderMessageContract() {
		return new GanglandTraderMessages();
	}

	@Bean
	public ShopDisplayResolver shopDisplayResolver(WeaponService weaponService) {
		return new GanglandShopDisplayResolver(weaponService);
	}

	// ── Purchase / barter / sell services ────────────────────────────────

	@Bean
	public ShopPurchaseService shopPurchaseService(ItemRefresherRegistry refresherRegistry) {
		return new ShopPurchaseService(refresherRegistry);
	}

	@Bean
	public ShopBarterService shopBarterService(ItemRefresherRegistry refresherRegistry) {
		return new ShopBarterService(refresherRegistry);
	}

	@Bean
	public ShopSellService shopSellService() {
		return new ShopSellService();
	}

	@Bean
	public SellValuator sellValuator(ItemSerializerRegistry serializerRegistry) {
		return new CategorySellValuator(serializerRegistry);
	}

	@Bean
	public CategoryBarterValuator categoryBarterValuator(ItemSerializerRegistry serializerRegistry) {
		return new CategoryBarterValuator(serializerRegistry);
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

	// ── Shop registry (per-shop YAML via FolderLoader) ───────────────────

	@Bean
	public ShopRegistry shopRegistry(FileManager fileManager, ShopYamlReader reader, ShopYamlWriter writer) {
		ShopRegistry registry = new ShopRegistry(gangland, fileManager, reader, writer);
		registry.initialize();
		return registry;
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

	@Bean
	public PriceEditorView priceEditorView(TraderSettings traderSettings) {
		return new PriceEditorView(gangland, traderSettings);
	}

	@Bean
	public SellCategoryItemsAdminView sellCategoryItemsAdminView(ItemRefresherRegistry refresherRegistry,
	                                                             ShopDisplayResolver displayResolver) {
		return new SellCategoryItemsAdminView(gangland, refresherRegistry, displayResolver);
	}

	@Bean
	public BarterCategoryItemsAdminView barterCategoryItemsAdminView(ItemRefresherRegistry refresherRegistry,
	                                                                 ShopDisplayResolver displayResolver) {
		return new BarterCategoryItemsAdminView(gangland, refresherRegistry, displayResolver);
	}

	@Bean
	public ShopAdminView shopAdminView(ItemRefresherRegistry refresherRegistry,
	                                   ShopMessageContract shopMessages, TraderSettings traderSettings,
	                                   ShopDisplayResolver displayResolver) {
		return new ShopAdminView(gangland, refresherRegistry, shopMessages, traderSettings, displayResolver);
	}

	@Bean
	public ShopAdminFlow shopAdminFlow(ItemRefresherRegistry refresherRegistry,
	                                   ShopAdminView adminPanel,
	                                   PriceEditorView priceEditorPanel,
	                                   SellCategoryItemsAdminView sellCategoryPanel,
	                                   BarterCategoryItemsAdminView barterCategoryPanel) {
		return new ShopAdminFlow(gangland, refresherRegistry, adminPanel, priceEditorPanel, sellCategoryPanel,
		                         barterCategoryPanel);
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
