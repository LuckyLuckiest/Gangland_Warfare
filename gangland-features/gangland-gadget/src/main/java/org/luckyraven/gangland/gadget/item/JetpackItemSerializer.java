package org.luckyraven.gangland.gadget.item;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.gadget.jetpack.JetpackKey;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.keystone.item.ItemSerializer;

/** Extracts the jetpack registry id from {@link JetpackKey#JETPACK_ID}. */
public final class JetpackItemSerializer implements ItemSerializer {

	@Override
	public ItemKind kind() {
		return ItemKind.JETPACK;
	}

	@Override
	@Nullable
	public String extract(ItemStack stack) {
		return new ItemBuilder(stack).getStringTagData(JetpackKey.JETPACK_ID.getKey());
	}
}
