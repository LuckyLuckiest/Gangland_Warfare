package me.luckyraven.item.serializer;

import me.luckyraven.gadget.repair.RepairKeys;
import me.luckyraven.item.ItemKind;
import me.luckyraven.item.ItemSerializer;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts the repair-material id stamped by the repair system under {@link RepairKeys#REPAIR_MATERIAL_ID}.
 */
public final class RepairItemSerializer implements ItemSerializer {

	@Override
	public ItemKind kind() {
		return ItemKind.REPAIR;
	}

	@Override
	@Nullable
	public String extract(ItemStack stack) {
		return new ItemBuilder(stack).getStringTagData(RepairKeys.REPAIR_MATERIAL_ID);
	}
}
