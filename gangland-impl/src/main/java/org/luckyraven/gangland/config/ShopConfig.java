package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.file.configuration.shop.GanglandShopMessages;
import org.luckyraven.gangland.file.configuration.shop.GanglandShopUiSettings;
import org.luckyraven.gangland.shop.ShopAdminOpener;
import org.luckyraven.gangland.shop.ShopAdminOpenerImpl;
import org.luckyraven.gangland.shop.admin.view.*;
import org.luckyraven.gangland.shop.config.ShopUiSettings;
import org.luckyraven.keystone.item.ItemConverterRegistry;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.item.ItemSerializerRegistry;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.shop.ShopRegistry;
import org.luckyraven.keystone.shop.io.ShopYamlReader;
import org.luckyraven.keystone.shop.io.ShopYamlWriter;
import org.luckyraven.keystone.shop.message.DefaultShopDisplayResolver;
import org.luckyraven.keystone.shop.message.ShopDisplayResolver;
import org.luckyraven.keystone.shop.message.ShopMessageContract;
import org.luckyraven.keystone.shop.transaction.ShopBarterService;
import org.luckyraven.keystone.shop.transaction.ShopPurchaseService;
import org.luckyraven.keystone.shop.transaction.ShopSellService;
import org.luckyraven.keystone.shop.valuation.CategoryBarterValuator;
import org.luckyraven.keystone.shop.valuation.CategorySellValuator;
import org.luckyraven.keystone.shop.valuation.SellValuator;

/**
 * CONFIG-phase wiring for the shop layer used by both the trader NPC (cops-n-crooks module) and the admin
 * editor: registry I/O, purchase/barter/sell services and valuators now come from Keystone's {@code keystone-shop}
 * (WS4 G1a — {@code gangland-ui/shop-api} deleted, its 26 headless classes promoted upstream in Keystone's G0).
 * The 5 admin-view beans stay wired to the relocated {@code gangland-impl}-local views (still on inventory-api,
 * unchanged this gate — the {@code MenuFlow} rewrite is G1b). This class must keep working with zero modules
 * installed, since {@code ShopCommand} injects {@link ShopRegistry} and {@link ShopAdminFlow} directly.
 *
 * <p>{@link #shopUiSettings} is constructed inline, never a {@code @Bean}: {@code TraderSettings} (cops-n-crooks)
 * extends {@link ShopUiSettings}, so a module-side {@code TraderSettings} bean would also register under
 * {@link ShopUiSettings} — publishing a core bean of that type too would make resolution order-dependent.
 */
@CustomLog
@Configuration
public class ShopConfig {

	private final Gangland gangland;

	private final ShopUiSettings shopUiSettings = new GanglandShopUiSettings();

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

	// ── Message contracts ────────────────────────────────────────────────

	@Bean
	public ShopMessageContract shopMessageContract() {
		return new GanglandShopMessages();
	}

	@Bean
	public ShopDisplayResolver shopDisplayResolver(ItemSerializerRegistry serializerRegistry,
	                                               ItemConverterRegistry converterRegistry) {
		return new DefaultShopDisplayResolver(serializerRegistry, converterRegistry);
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

	// ── Shop registry (per-shop YAML via FolderLoader) ───────────────────

	@Bean
	public ShopRegistry shopRegistry(FileManager fileManager, ShopYamlReader reader, ShopYamlWriter writer) {
		ShopRegistry registry = new ShopRegistry(gangland, fileManager, reader, writer);
		registry.initialize();
		return registry;
	}

	// ── Admin views ──────────────────────────────────────────────────────

	@Bean
	public PriceEditorView priceEditorView() {
		return new PriceEditorView(gangland, shopUiSettings);
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
	                                   ShopMessageContract shopMessages,
	                                   ShopDisplayResolver displayResolver) {
		return new ShopAdminView(gangland, refresherRegistry, shopMessages, shopUiSettings, displayResolver);
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

	/**
	 * WS4 G1a, B1: the module-facing seam — npc-shops (and any future module) resolves this bean type instead of
	 * naming {@link ShopAdminFlow} directly.
	 */
	@Bean
	public ShopAdminOpener shopAdminOpener(ShopAdminFlow shopAdminFlow) {
		return new ShopAdminOpenerImpl(shopAdminFlow);
	}

}
