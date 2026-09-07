package org.luckyraven.gangland.file.configuration.shop;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.gangland.shop.message.ShopDisplayResolver;

import java.util.List;
import java.util.function.Supplier;

/**
 * Default {@link ShopDisplayResolver} for gangland. Resolves an item's clean display name by asking every
 * registered {@link ShopDisplayNameProvider} in turn — a runtime module registers one so the clean configured
 * display name is returned instead of the item's live display name (which may carry dynamic decoration appended at
 * runtime). Items no provider claims fall back to the stored display name or a humanised material name.
 */
public final class GanglandShopDisplayResolver implements ShopDisplayResolver {

	private final Supplier<List<ShopDisplayNameProvider>> providers;

	public GanglandShopDisplayResolver(Supplier<List<ShopDisplayNameProvider>> providers) {
		this.providers = providers;
	}

	@Override
	public String cleanDisplayName(ItemStack item) {
		if (item == null) return "item";

		for (ShopDisplayNameProvider provider : providers.get()) {
			String resolved = provider.cleanDisplayName(item);
			if (resolved != null && !resolved.isBlank()) return resolved;
		}

		if (item.hasItemMeta()) {
			ItemMeta meta = item.getItemMeta();
			if (meta != null && meta.hasDisplayName()) {
				String displayName = meta.getDisplayName();
				if (!displayName.isBlank()) return ChatUtil.color(displayName);
			}
		}

		return ChatUtil.color(ChatUtil.capitalize(item.getType().name().toLowerCase().replace('_', ' ')));
	}

}
