package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.file.configuration.shop.GanglandShopDisplayResolver;
import org.luckyraven.gangland.file.configuration.shop.GanglandShopMessages;
import org.luckyraven.gangland.file.configuration.shop.GanglandShopUiSettings;
import org.luckyraven.gangland.item.ItemRefresherRegistry;
import org.luckyraven.gangland.item.ItemSerializerRegistry;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.gangland.shop.ShopRegistry;
import org.luckyraven.gangland.shop.config.ShopUiSettings;
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

/**
 * CONFIG-phase wiring for the shop-api layer used by both the trader NPC (cops-n-crooks module) and the admin
 * editor: registry I/O, purchase/barter/sell services, valuators and the admin views. Trader-specific beans moved
 * to {@code TraderModuleConfig} (T15, module split sprint 2026-09-07) — this class must keep working with zero
 * modules installed, since {@code ShopCommand} injects {@link ShopRegistry} and {@link ShopAdminFlow} directly.
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

}
