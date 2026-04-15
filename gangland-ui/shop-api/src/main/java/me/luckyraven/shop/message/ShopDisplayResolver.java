package me.luckyraven.shop.message;

import org.bukkit.inventory.ItemStack;

/**
 * Resolves the display name that should be shown for a shop entry. For plain items this is just the custom display name
 * (or a humanised material name). For domain items — notably weapons — the item's live display name includes transient
 * state (e.g. the magazine counter "15/30") that must be stripped before the entry is rendered in a shop GUI or
 * reported in a purchase-success message.
 *
 * <p>Integrations implement this contract so shop-api stays decoupled from weapon/wearable/unique-item services.
 */
public interface ShopDisplayResolver {

	/**
	 * @return a clean display name suitable for shop UI and success messages — without live state like ammo counters.
	 */
	String cleanDisplayName(ItemStack item);

}
