package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.copsncrooks.npc.trader.ShopViewOpener;
import me.luckyraven.copsncrooks.npc.trader.ShopViewOpenerImpl;
import me.luckyraven.copsncrooks.npc.trader.TraderData;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.copsncrooks.npc.trader.mood.BargainCooldownService;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.respawn.TraderRespawnService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitRegistry;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitsLoader;
import me.luckyraven.copsncrooks.npc.trader.view.*;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.copsncrooks.GanglandTraderMessages;
import me.luckyraven.file.configuration.shop.GanglandShopDisplayResolver;
import me.luckyraven.file.configuration.shop.GanglandShopMessages;
import me.luckyraven.file.configuration.shop.TraderSettingsImpl;
import me.luckyraven.item.ItemRefresherRegistry;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.shop.io.ShopYamlReader;
import me.luckyraven.shop.io.ShopYamlWriter;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.message.ShopMessageContract;
import me.luckyraven.shop.transaction.ShopBarterService;
import me.luckyraven.shop.transaction.ShopPurchaseService;
import me.luckyraven.shop.transaction.ShopSellService;
import me.luckyraven.shop.transaction.ShopTradeInService;
import me.luckyraven.shop.valuation.CategoryBarterValuator;
import me.luckyraven.shop.valuation.CategorySellValuator;
import me.luckyraven.shop.valuation.SellValuator;
import me.luckyraven.shop.view.BarterCategoryItemsAdminView;
import me.luckyraven.shop.view.PriceEditorView;
import me.luckyraven.shop.view.SellCategoryItemsAdminView;
import me.luckyraven.shop.view.ShopAdminView;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.weapon.WeaponService;

import java.io.IOException;

@CustomLog
@Configuration
public class ShopConfig {

	private final Gangland gangland;

	public ShopConfig(Gangland gangland) {
		this.gangland = gangland;
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

	// ── Purchase / barter / sell / trade-in services ─────────────────────

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
	public ShopTradeInService shopTradeInService(ItemRefresherRegistry refresherRegistry) {
		return new ShopTradeInService(refresherRegistry);
	}

	@Bean
	public SellValuator sellValuator() {
		return new CategorySellValuator();
	}

	@Bean
	public CategoryBarterValuator categoryBarterValuator() {
		return new CategoryBarterValuator();
	}

	// ── Traits (loaded from plugin/trader_traits.yml) ────────────────────

	@Bean
	public TraderTraitRegistry traderTraitRegistry() {
		return new TraderTraitRegistry();
	}

	@Bean
	public TraderTraitsLoader traderTraitsLoader(TraderTraitRegistry registry, FileManager fileManager) {
		TraderTraitsLoader loader = new TraderTraitsLoader(registry);

		try {
			FileHandler handler = new FileHandler(gangland, "trader_traits", "", "yml");
			handler.create(true);
			if (!fileManager.contains("trader_traits")) fileManager.addFile(handler, true);
			loader.load(handler);
		} catch (IOException e) {
			log.error("Failed to initialize trader_traits.yml: {}", e.getMessage(), e);
		}

		return loader;
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
	public TraderSettings traderSettings(Settings settings) {
		return new TraderSettingsImpl();
	}

	// ── Views ────────────────────────────────────────────────────────────

	@Bean
	public BargainCooldownService bargainCooldownService(TraderSettings traderSettings) {
		return new BargainCooldownService(traderSettings);
	}

	@Bean
	public BargainView bargainView(MoodService moodService, BargainCooldownService cooldownService,
	                               TraderMessageContract traderMessages,
	                               TraderSettings traderSettings, ShopDisplayResolver displayResolver) {
		return new BargainView(gangland, moodService, cooldownService, traderMessages, traderSettings,
		                       displayResolver);
	}

	@Bean
	public TipView tipView(TraderSettings traderSettings) {
		return new TipView(gangland, traderSettings);
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
	public TradeInView tradeInView(MoodService moodService, SellValuator sellValuator,
	                               ItemRefresherRegistry refresherRegistry,
	                               ShopDisplayResolver displayResolver,
	                               TraderSettings traderSettings) {
		return new TradeInView(gangland, moodService, sellValuator, refresherRegistry, displayResolver,
		                       traderSettings);
	}

	@Bean
	public NegotiationView negotiationView(MoodService moodService, BargainView bargainView,
	                                       TipView tipView, BarterView barterView,
	                                       TradeInView tradeInView,
	                                       TraderSettings traderSettings, TraderMessageContract traderMessages,
	                                       ShopDisplayResolver displayResolver) {
		NegotiationView view = new NegotiationView(gangland, moodService, traderSettings, traderMessages,
		                                           displayResolver);
		view.setSubViews(bargainView, tipView, barterView, tradeInView);
		return view;
	}

	@Bean
	public SellBargainView sellBargainView(MoodService moodService, BargainCooldownService cooldownService,
	                                       TraderMessageContract traderMessages,
	                                       TraderSettings traderSettings) {
		return new SellBargainView(gangland, moodService, cooldownService, traderMessages, traderSettings);
	}

	@Bean
	public SellView traderSellView(MoodService moodService, SellValuator sellValuator,
	                               ItemRefresherRegistry refresherRegistry,
	                               TraderSettings traderSettings, TraderMessageContract traderMessages,
	                               ShopDisplayResolver displayResolver,
	                               SellBargainView sellBargainView) {
		SellView view = new SellView(gangland, moodService, sellValuator, refresherRegistry,
		                             traderSettings, traderMessages, displayResolver);
		view.setBargainView(sellBargainView);
		return view;
	}

	@Bean
	public ShopView traderShopView(MoodService moodService, NegotiationView negotiationView,
	                               TraderSettings traderSettings, ShopDisplayResolver displayResolver) {
		return new ShopView(gangland, moodService, negotiationView, traderSettings, displayResolver);
	}

	@Bean
	public ModeSelectView traderModeSelectView(TraderSettings traderSettings,
	                                           ShopView shopView,
	                                           SellView sellView) {
		ModeSelectView view = new ModeSelectView(gangland, traderSettings);
		view.setSubViews(shopView, sellView);
		shopView.setModeSelectView(view);
		sellView.setModeSelectView(view);
		return view;
	}

	@Bean
	public PriceEditorView priceEditorView(TraderSettings traderSettings) {
		return new PriceEditorView(gangland, traderSettings);
	}

	@Bean
	public SellCategoryItemsAdminView sellCategoryItemsAdminView(PriceEditorView priceEditorView,
	                                                             ItemRefresherRegistry refresherRegistry,
	                                                             ShopDisplayResolver displayResolver) {
		return new SellCategoryItemsAdminView(gangland, priceEditorView, refresherRegistry, displayResolver);
	}

	@Bean
	public BarterCategoryItemsAdminView barterCategoryItemsAdminView(PriceEditorView priceEditorView,
	                                                                 ItemRefresherRegistry refresherRegistry,
	                                                                 ShopDisplayResolver displayResolver) {
		return new BarterCategoryItemsAdminView(gangland, priceEditorView, refresherRegistry, displayResolver);
	}

	@Bean
	public ShopAdminView shopAdminView(PriceEditorView priceEditorView,
	                                   SellCategoryItemsAdminView categoryItemsView,
	                                   BarterCategoryItemsAdminView barterItemsView,
	                                   ItemRefresherRegistry refresherRegistry,
	                                   ShopMessageContract shopMessages, TraderSettings traderSettings,
	                                   ShopDisplayResolver displayResolver) {
		return new ShopAdminView(gangland, priceEditorView, categoryItemsView, barterItemsView, refresherRegistry,
		                         shopMessages, traderSettings, displayResolver);
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
	                                     ModeSelectView modeSelectView, ShopAdminView adminView,
	                                     ShopMessageContract shopMessages,
	                                     TraderMessageContract traderMessages) {
		return new ShopViewOpenerImpl(traderManager, shopRegistry, modeSelectView, adminView,
		                              shopMessages, traderMessages);
	}

}
