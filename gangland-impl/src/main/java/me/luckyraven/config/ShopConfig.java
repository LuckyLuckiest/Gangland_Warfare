package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.copsncrooks.npc.trader.ShopViewOpener;
import me.luckyraven.copsncrooks.npc.trader.ShopViewOpenerImpl;
import me.luckyraven.copsncrooks.npc.trader.TraderData;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
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
import me.luckyraven.shop.view.PriceEditorView;
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

	// ── Purchase / barter services (reusable across any shop integration) ─

	@Bean
	public ShopPurchaseService shopPurchaseService(ItemRefresherRegistry refresherRegistry) {
		return new ShopPurchaseService(refresherRegistry);
	}

	@Bean
	public ShopBarterService shopBarterService(ItemRefresherRegistry refresherRegistry) {
		return new ShopBarterService(refresherRegistry);
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
			handler.create(true); // create from jar resource if present
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
	public BargainView bargainView(MoodService moodService, TraderMessageContract traderMessages,
	                               TraderSettings traderSettings, ShopDisplayResolver displayResolver) {
		return new BargainView(gangland, moodService, traderMessages, traderSettings, displayResolver);
	}

	@Bean
	public TipView tipView(TraderSettings traderSettings) {
		return new TipView(gangland, traderSettings);
	}

	@Bean
	public BarterConfirmView barterConfirmView(TraderSettings traderSettings, ShopDisplayResolver displayResolver) {
		return new BarterConfirmView(gangland, traderSettings, displayResolver);
	}

	@Bean
	public NegotiationView negotiationView(MoodService moodService, BargainView bargainView,
	                                       TipView tipView, BarterConfirmView barterView,
	                                       TraderSettings traderSettings, ShopDisplayResolver displayResolver) {
		NegotiationView view = new NegotiationView(gangland, moodService, traderSettings, displayResolver);
		view.setSubViews(bargainView, tipView, barterView);
		return view;
	}

	@Bean
	public TraderShopView traderShopView(MoodService moodService, NegotiationView negotiationView,
	                                     TraderSettings traderSettings, ShopDisplayResolver displayResolver) {
		return new TraderShopView(gangland, moodService, negotiationView, traderSettings, displayResolver);
	}

	@Bean
	public PriceEditorView priceEditorView(TraderSettings traderSettings) {
		return new PriceEditorView(gangland, traderSettings);
	}

	@Bean
	public ShopAdminView shopAdminView(PriceEditorView priceEditorView, ItemRefresherRegistry refresherRegistry,
	                                   ShopMessageContract shopMessages, TraderSettings traderSettings,
	                                   ShopDisplayResolver displayResolver) {
		return new ShopAdminView(gangland, priceEditorView, refresherRegistry, shopMessages, traderSettings,
		                         displayResolver);
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
	                                     TraderShopView playerView, ShopAdminView adminView,
	                                     ShopMessageContract shopMessages,
	                                     TraderMessageContract traderMessages) {
		return new ShopViewOpenerImpl(traderManager, shopRegistry, playerView, adminView,
		                              shopMessages, traderMessages);
	}

}
