package org.luckyraven.gangland.file.configuration.shop;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.shop.config.ShopUiSettings;

/**
 * Core-side {@link ShopUiSettings} implementation, constructed inline (never a {@code @Bean}) by shop-admin views
 * that must work with zero modules installed. {@code TraderSettings} (cops-n-crooks) also extends
 * {@link ShopUiSettings}, so core must never publish a bean of that type — {@code DependencyContainer.getInstance}
 * would resolve whichever bean landed first (T15, module split sprint 2026-09-07).
 */
public final class GanglandShopUiSettings implements ShopUiSettings {

	@Override
	public int getMaxModeMultiplier() {
		return Settings.getShopMaxModeMultiplier();
	}

	@Override
	public String getInventoryFillName() {
		return Settings.getInventoryFillName();
	}

	@Override
	public String getInventoryFillItem() {
		return Settings.getInventoryFillItem();
	}

}
