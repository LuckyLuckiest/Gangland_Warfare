package org.luckyraven.gangland.shop.config;

/**
 * UI-layer settings shared by every shop view (admin editor, trader browser, negotiation sub-views). Integrations
 * expose a wrapper around their native {@code Settings} that implements this interface so shop-api views never reach
 * into a specific feature module's config.
 */
public interface ShopUiSettings {

	/**
	 * @return upper bound for the price-editor mode multiplier (sanity-cap on admin input).
	 */
	int getMaxModeMultiplier();

	/**
	 * @return display name (colour-coded) for the glass-pane border / filler used by shop inventories.
	 */
	String getInventoryFillName();

	/**
	 * @return template item for the glass-pane border / filler used by shop inventories.
	 */
	String getInventoryFillItem();

}
