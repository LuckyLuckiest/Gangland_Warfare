package org.luckyraven.gangland.inventory.multi;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.ItemBuilder;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.inventory.InventoryHandler;

/**
 * A single dynamic entry in a multi-inventory's paged list — the rendered ItemStack plus its optional click action.
 * Non-clickable entries use {@code onClick == null}.
 */
public record ListEntry(ItemStack item, @Nullable TriConsumer<Player, InventoryHandler, ItemBuilder> onClick) {

	public static ListEntry of(ItemStack item) {
		return new ListEntry(item, null);
	}

}
