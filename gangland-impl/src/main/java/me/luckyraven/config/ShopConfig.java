package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.bootstrap.GanglandContext;
import me.luckyraven.copsncrooks.npc.trader.ShopViewOpener;
import me.luckyraven.copsncrooks.npc.trader.ShopViewOpenerImpl;
import me.luckyraven.copsncrooks.npc.trader.TraderData;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.economy.TraderEconomyContract;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.respawn.TraderRespawnService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitRegistry;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitsLoader;
import me.luckyraven.copsncrooks.npc.trader.view.*;
import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.Configuration;
import me.luckyraven.core.bean.PostConstruct;
import me.luckyraven.core.bean.Qualifier;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.copsncrooks.GanglandTraderEconomy;
import me.luckyraven.file.configuration.copsncrooks.GanglandTraderMessages;
import me.luckyraven.file.configuration.shop.GanglandShopDisplayResolver;
import me.luckyraven.file.configuration.shop.GanglandShopMessages;
import me.luckyraven.file.configuration.shop.TraderSettingsImpl;
import me.luckyraven.item.ItemRefresherRegistry;
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
import me.luckyraven.shop.valuation.CategoryBarterValuator;
import me.luckyraven.shop.valuation.CategorySellValuator;
import me.luckyraven.shop.valuation.SellValuator;
import me.luckyraven.shop.view.*;
import me.luckyraven.weapon.WeaponService;
import org.bukkit.entity.Player;

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
	public TraderSettings traderSettings(Settings settings) {
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
	public NegotiationView negotiationView(MoodService moodService, BarterView barterView,
	                                       QuantitySelectorView quantitySelectorView,
	                                       TraderSettings traderSettings, TraderMessageContract traderMessages,
	                                       TraderEconomyContract economy, ShopDisplayResolver displayResolver) {
		return new NegotiationView(gangland, moodService, traderSettings, traderMessages, economy, displayResolver,
		                           barterView, quantitySelectorView);
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
	public ModeSelectView traderModeSelectView(TraderSettings traderSettings, SellView sellView) {
		return new ModeSelectView(gangland, traderSettings, sellView);
	}

	@Bean
	public TraderFlow traderFlow(ModeSelectView modeSelectView, ShopView shopView, NegotiationView negotiationView,
	                             SellView sellView) {
		TraderFlow flow = new TraderFlow(gangland, modeSelectView, shopView, negotiationView);
		// Circular: SellView returns to the mode-select panel by restarting the flow. Wired via setter so the
		// SellView bean can be constructed before TraderFlow exists.
		sellView.setTraderFlow(flow);
		return flow;
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
	                                     TraderFlow traderFlow, ShopAdminView adminView,
	                                     ShopMessageContract shopMessages,
	                                     TraderMessageContract traderMessages) {
		return new ShopViewOpenerImpl(traderManager, shopRegistry, traderFlow, adminView,
		                              shopMessages, traderMessages);
	}

}
