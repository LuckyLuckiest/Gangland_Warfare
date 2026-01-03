package me.luckyraven.loot.item;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for providing actual ItemStacks from reference IDs This allows the loot system to be decoupled from
 * specific implementations
 */
public interface LootItemProvider {

	/**
	 * Gets an ItemStack for a weapon by its configuration key
	 */
	@Nullable ItemStack getWeapon(String weaponId);

	/**
	 * Gets an ItemStack for ammunition by its configuration key
	 */
	@Nullable ItemStack getAmmunition(String ammoId, int amount);

	/**
	 * Gets an ItemStack for a unique item by its configuration key
	 */
	@Nullable ItemStack getUniqueItem(String uniqueId);

	/**
	 * Gets an ItemStack for a repair item by its configuration key
	 */
	@Nullable ItemStack getRepairItem(String repairId, int amount);

	/**
	 * Gets an ItemStack for a consumable by its configuration key
	 */
	@Nullable ItemStack getConsumable(String consumableId, int amount);

	/**
	 * Gets an ItemStack for a material by its configuration key
	 */
	@Nullable ItemStack getMaterial(String materialId, int amount);

	/**
	 * Gets an ItemStack for a miscellaneous item by its configuration key
	 */
	@Nullable ItemStack getMiscItem(String miscId, int amount);

	/**
	 * Checks if a weapon exists
	 */
	boolean hasWeapon(String weaponId);

	/**
	 * Checks if ammunition exists
	 */
	boolean hasAmmunition(String ammoId);

	/**
	 * Checks if a unique item exists
	 */
	boolean hasUniqueItem(String uniqueId);

	/**
	 * Checks if a repair item exists
	 */
	boolean hasRepairItem(String repairId);
}
