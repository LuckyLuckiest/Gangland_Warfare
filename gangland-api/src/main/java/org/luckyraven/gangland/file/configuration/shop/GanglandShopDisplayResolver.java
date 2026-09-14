package org.luckyraven.gangland.file.configuration.shop;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.luckyraven.keystone.item.ItemConverterRegistry;
import org.luckyraven.keystone.item.ItemSerializerRegistry;
import org.luckyraven.keystone.item.spi.ItemDefinitions;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.gangland.shop.message.ShopDisplayResolver;

/**
 * Default {@link ShopDisplayResolver} for gangland. Resolves an item's clean display name from the <em>live</em>
 * stack's stored display name first — required for a kind whose converter round trip is non-deterministic (a money
 * stack's {@code pristine} rebuild re-rolls its amount into the name, review B4). Falls back to rebuilding the item
 * factory-fresh through {@link ItemDefinitions#pristine} (serialize -&gt; definition -&gt; convert) and reading
 * <em>that</em> stack's display name — this strips whatever dynamic decoration the live item picked up at runtime
 * (e.g. an ammo count baked into a weapon's name) the same way a module's {@code ShopDisplayNameProvider} used to,
 * without the core needing to know which module owns the item. Items with no display name at all, live or pristine,
 * fall back to a humanised material name.
 */
public final class GanglandShopDisplayResolver implements ShopDisplayResolver {

	private final ItemSerializerRegistry serializers;
	private final ItemConverterRegistry  converters;

	public GanglandShopDisplayResolver(ItemSerializerRegistry serializers, ItemConverterRegistry converters) {
		this.serializers = serializers;
		this.converters  = converters;
	}

	@Override
	public String cleanDisplayName(ItemStack item) {
		if (item == null) return "item";

		String fromLiveItem = displayNameOf(item);
		if (fromLiveItem != null) return fromLiveItem;

		ItemStack pristine = ItemDefinitions.pristine(serializers, converters, item);
		String    fromPristine = pristine == null ? null : displayNameOf(pristine);
		if (fromPristine != null) return fromPristine;

		return ChatUtil.color(ChatUtil.capitalize(item.getType().name().toLowerCase().replace('_', ' ')));
	}

	private static String displayNameOf(ItemStack stack) {
		if (!stack.hasItemMeta()) return null;

		ItemMeta meta = stack.getItemMeta();
		if (meta == null || !meta.hasDisplayName()) return null;

		String displayName = meta.getDisplayName();
		return displayName.isBlank() ? null : ChatUtil.color(displayName);
	}

}
