package me.luckyraven.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Rebuilds an existing {@link ItemStack} into a factory-fresh copy.
 * <p>
 * Implementations inspect the item (typically via NBT tags) to decide whether they own it and, if so, construct a new
 * instance with default state (full ammo, full durability, full repairs, etc.). Used by the shop/trader subsystem so
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

}
