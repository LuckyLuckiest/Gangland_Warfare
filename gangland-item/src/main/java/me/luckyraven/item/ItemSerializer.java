package me.luckyraven.item;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Reverse of {@link ItemConverter}: takes an {@link ItemStack} and produces the value part of its canonical id (e.g.
 * {@code "ak47"} for a weapon). The {@link ItemKind} returned by {@link #kind()} supplies the label so that the
 * registry can compose the full {@code "<label>:<value>"} id.
 *
 * <p>Matching is not performed here — the {@link ItemSerializerRegistry} takes a {@code Predicate<ItemStack>} at
 * registration time and only calls {@link #extract(ItemStack)} once the predicate has matched.
 */
public interface ItemSerializer {

	ItemKind kind();

	@Nullable
	String extract(ItemStack stack);
}
