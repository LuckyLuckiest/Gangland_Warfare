package me.luckyraven.inventory.loot.item;

import lombok.Builder;
import lombok.Getter;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.inventory.ItemStack;

/**
 * Represents a configurable loot item with weight-based chance
 */
@Getter
@Builder
public class LootItem {

	private final String       id;
	private final ItemBuilder  itemBuilder;
	private final int          minAmount;
	private final int          maxAmount;
	private final double       weight;
	private final LootCategory category;
	private final String       tierRequirement;

	/**
	 * Creates a random amount ItemStack from this loot item
	 */
	public ItemStack createItem() {
		int amount = minAmount + (int) (Math.random() * (maxAmount - minAmount + 1));
		return itemBuilder.clone().setAmount(amount).build();
	}

	public enum LootCategory {
		WEAPON,
		AMMO,
		UNIQUE,
		REPAIR,
		CONSUMABLE,
		MATERIAL,
		MISC
	}

}
