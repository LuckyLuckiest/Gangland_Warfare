package me.luckyraven.weapon.repair.api;

import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.repair.RepairManager;
import me.luckyraven.weapon.repair.item.RepairItem;
import me.luckyraven.weapon.repair.item.RepairItemManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Public API for the weapon repair system.
 * <p>
 * Use this API to interact with the repair system from external plugins.
 */
public class RepairAPI {

	private final RepairManager repairManager;

	public RepairAPI(@NotNull RepairManager repairManager) {
		this.repairManager = repairManager;
	}

	/**
	 * Applies a repair to a weapon using a repair item.
	 *
	 * @param player The player performing the repair
	 * @param weapon The weapon to repair
	 * @param repairItem The repair item ItemStack
	 *
	 * @return true if the repair was successful
	 */
	public boolean applyRepair(@NotNull Player player, @NotNull Weapon weapon, @NotNull ItemStack repairItem) {
		return repairManager.applyRepair(player, weapon, repairItem);
	}

	/**
	 * Registers a custom repair effect.
	 *
	 * @param id The effect type ID (must be unique)
	 * @param effect The effect implementation
	 */
	public void registerRepairEffect(@NotNull String id, @NotNull RepairEffect effect) {
		repairManager.registerRepairEffect(id, effect);
	}

	/**
	 * Gets a repair item from an ItemStack.
	 *
	 * @param itemStack The ItemStack to check
	 *
	 * @return The RepairItem, or null if not a repair item
	 */
	@Nullable
	public RepairItem getRepairItem(@NotNull ItemStack itemStack) {
		return repairManager.getRepairItemManager().getRepairItem(itemStack);
	}

	/**
	 * Gets a repair item by ID.
	 *
	 * @param id The repair item ID
	 *
	 * @return The RepairItem, or null if not found
	 */
	@Nullable
	public RepairItem getRepairItemById(@NotNull String id) {
		return repairManager.getRepairItemManager().getRepairItem(id);
	}

	/**
	 * Gets all registered repair items.
	 *
	 * @return A map of repair item ID to RepairItem
	 */
	@NotNull
	public Map<String, RepairItem> getAllRepairItems() {
		return repairManager.getRepairItemManager().getAllRepairItems();
	}

	/**
	 * Gets all registered repair effects.
	 *
	 * @return A map of effect ID to RepairEffect
	 */
	@NotNull
	public Map<String, RepairEffect> getAllRepairEffects() {
		return repairManager.getAllRepairEffects();
	}

	/**
	 * Registers a repair item programmatically.
	 *
	 * @param id The repair item ID
	 * @param repairItem The repair item
	 */
	public void registerRepairItem(@NotNull String id, @NotNull RepairItem repairItem) {
		repairManager.getRepairItemManager().registerRepairItem(id, repairItem);
	}

	/**
	 * Checks if an ItemStack is a repair item.
	 *
	 * @param itemStack The ItemStack to check
	 *
	 * @return true if it's a repair item
	 */
	public boolean isRepairItem(@Nullable ItemStack itemStack) {
		return RepairItem.isRepairItem(itemStack);
	}

	/**
	 * Checks if a repair item can be used (has durability remaining).
	 *
	 * @param itemStack The repair item ItemStack
	 *
	 * @return true if the item can be used
	 */
	public boolean canUseRepairItem(@NotNull ItemStack itemStack) {
		return RepairItem.canUse(itemStack);
	}

	/**
	 * Gets the repair item manager.
	 *
	 * @return The repair item manager
	 */
	@NotNull
	public RepairItemManager getRepairItemManager() {
		return repairManager.getRepairItemManager();
	}

	/**
	 * Reloads the repair system configuration.
	 */
	public void reload() {
		repairManager.reload();
	}
}