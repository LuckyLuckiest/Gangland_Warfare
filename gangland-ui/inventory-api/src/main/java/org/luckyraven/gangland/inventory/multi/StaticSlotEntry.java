package org.luckyraven.gangland.inventory.multi;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.gangland.inventory.InventoryHandler;

/**
 * A static item pinned to an explicit inventory slot on every page of a {@link MultiInventory}. The slot index is
 * authoritative — items are placed at exactly the slot declared in the YAML {@code Static_Items} section, not inferred
 * from their order or collapsed onto column 1.
 */
public record StaticSlotEntry(ItemStack item, TriConsumer<Player, InventoryHandler, ItemBuilder> onClick) { }
