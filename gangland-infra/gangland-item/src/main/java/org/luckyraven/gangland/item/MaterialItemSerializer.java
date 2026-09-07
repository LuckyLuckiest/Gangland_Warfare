package org.luckyraven.gangland.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Fallback serializer — always paired with a trivial predicate. Returns the lowercased {@link Material} name so that
 * plain Bukkit items get a sensible id ({@code material:diamond}, {@code material:emerald_block}).
 */
public final class MaterialItemSerializer implements ItemSerializer {

	@Override
	public ItemKind kind() {
		return ItemKind.MATERIAL;
	}

	@Override
	@Nullable
	public String extract(ItemStack stack) {
		if (stack == null) {
			return null;
		}
		Material type = stack.getType();
		if (type == Material.AIR) {
			return null;
		}
		return type.name().toLowerCase();
	}
}
