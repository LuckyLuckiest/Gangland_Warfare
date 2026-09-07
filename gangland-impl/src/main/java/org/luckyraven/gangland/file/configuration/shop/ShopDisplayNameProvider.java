package org.luckyraven.gangland.file.configuration.shop;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A module's answer to "what is this item's clean shop display name". The core cannot name a module's item type, so
 * a module registers a bean implementing this contract; {@link GanglandShopDisplayResolver} pulls every
 * implementation out of the container, lazily, and uses the first non-blank result.
 */
public interface ShopDisplayNameProvider {

	/** @return the clean display name, or {@code null} when this provider does not own the item. */
	@Nullable
	String cleanDisplayName(ItemStack item);
}
