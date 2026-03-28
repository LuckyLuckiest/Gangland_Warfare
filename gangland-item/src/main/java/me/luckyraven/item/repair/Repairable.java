package me.luckyraven.item.repair;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface Repairable {

	@NotNull
	String getRepairableId();

	int getCurrentRepairDurability();

	void setCurrentRepairDurability(int durability);

	int getMaxRepairDurability();

	@NotNull
	RepairableType getRepairableType();

	@NotNull
	ItemStack buildItem();

	default boolean isFullyRepaired() {
		return getCurrentRepairDurability() >= getMaxRepairDurability();
	}

	default boolean canBeRepaired() {
		return getCurrentRepairDurability() < getMaxRepairDurability();
	}
}
