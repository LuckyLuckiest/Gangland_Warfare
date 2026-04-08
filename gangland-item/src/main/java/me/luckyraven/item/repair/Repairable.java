package me.luckyraven.item.repair;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

	/**
	 * Player-aware variant of {@link #buildItem()}. Implementations that resolve placeholders in their display name or
	 * lore should override this so {@code %gangland_*%} tokens render against the receiving viewer. The default
	 * delegates to the no-arg form for backwards compatibility.
	 */
	@NotNull
	default ItemStack buildItem(@Nullable Player player) {
		return buildItem();
	}

	default boolean isFullyRepaired() {
		return getCurrentRepairDurability() >= getMaxRepairDurability();
	}

	default boolean canBeRepaired() {
		return getCurrentRepairDurability() < getMaxRepairDurability();
	}
}
