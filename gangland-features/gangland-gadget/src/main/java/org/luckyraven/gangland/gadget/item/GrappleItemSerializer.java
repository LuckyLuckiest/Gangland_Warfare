package org.luckyraven.gangland.gadget.item;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gadget.grapple.GrappleKey;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.ItemSerializer;

/** Extracts the grapple registry id from {@link GrappleKey#GRAPPLE_ID}. */
public final class GrappleItemSerializer implements ItemSerializer {

	@Override
	public ItemKind kind() {
		return ItemKind.GRAPPLE;
	}

	@Override
	@Nullable
	public String extract(ItemStack stack) {
		return new ItemBuilder(stack).getStringTagData(GrappleKey.GRAPPLE_ID.getKey());
	}
}
