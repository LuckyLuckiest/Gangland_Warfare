package me.luckyraven.item;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Rebuilds an existing {@link ItemStack} into a factory-fresh copy.
 * <p>
 * Implementations inspect the item (typically via NBT tags) to decide whether they own it and, if so, construct a new
 * instance with default state (full ammo, full durability, etc.). Used by the shop/trader subsystem so
 * that every purchase or delivery produces a clean copy regardless of what the admin originally placed.
 */
public interface ItemRefresher {

	/**
	 * @return {@code true} if this refresher recognises {@code source} as one of its item types.
	 */
	boolean canRefresh(ItemStack source);

	/**
	 * Produces a fresh copy of {@code source}. Implementations must preserve the source's stack amount.
	 *
	 * @param source the stored item to refresh (may hold player-specific placeholders in display NBT)
	 * @param context the player the fresh copy is being built for (placeholders, gang-specific data, etc.); may be
	 *        {@code null} when refreshing outside a player context
	 *
	 * @return a freshly-built ItemStack, or {@code null} if this refresher cannot produce one
	 */
	@Nullable
	ItemStack refresh(ItemStack source, @Nullable Player context);

	/**
	 * Produces a copy of {@code source} with display fields (name, lore, custom model data, enchantment flags) rebuilt
	 * from the current config, but with runtime state (durability, ammo counter, use counter, etc.) preserved.
	 *
	 * <p>Used by the sell / trade-in paths, where the player is handing an item to the trader and the display needs
	 * to match the current shop template while the player's actual usage state must not be reset to factory-fresh.
	 *
	 * <p>The default implementation starts from {@link #refresh(ItemStack, Player)} and grafts the source's
	 * {@link Damageable} damage and enchantment levels back onto the fresh copy. Implementations that carry runtime
	 * state in custom NBT tags should override this to also copy those tags.
	 */
	@Nullable
	default ItemStack decorate(ItemStack source, @Nullable Player context) {
		ItemStack fresh = refresh(source, context);
		if (fresh == null || source == null) return fresh;

		ItemMeta freshMeta = fresh.getItemMeta();
		ItemMeta srcMeta   = source.getItemMeta();
		if (freshMeta != null && srcMeta != null) {
			if (freshMeta instanceof Damageable fd && srcMeta instanceof Damageable sd) {
				fd.setDamage(sd.getDamage());
			}
			fresh.setItemMeta(freshMeta);
		}

		Map<Enchantment, Integer> srcEnchants = source.getEnchantments();
		if (!srcEnchants.isEmpty()) {
			for (Map.Entry<Enchantment, Integer> entry : srcEnchants.entrySet()) {
				fresh.addUnsafeEnchantment(entry.getKey(), entry.getValue());
			}
		}
		return fresh;
	}

}
