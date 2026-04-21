package me.luckyraven.inventory.multi;

import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.inventory.InventoryHandler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A static item pinned to an explicit inventory slot on every page of a {@link MultiInventory}. The slot index is
 * authoritative — items are placed at exactly the slot declared in the YAML {@code Static_Items} section, not inferred
 * from their order or collapsed onto column 1.
 */
public record StaticSlotEntry(ItemStack item, TriConsumer<Player, InventoryHandler, ItemBuilder> onClick) { }
