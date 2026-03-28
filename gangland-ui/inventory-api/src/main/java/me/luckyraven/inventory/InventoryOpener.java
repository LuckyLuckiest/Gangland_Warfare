package me.luckyraven.inventory;

import org.bukkit.entity.Player;

/**
 * Functional interface for opening inventories by name This allows the inventory-api module to request inventory
 * opening without depending on the gangland-impl module
 */
@FunctionalInterface
public interface InventoryOpener {

	/**
	 * Opens an inventory for a player by name
	 *
	 * @param player the player to open the inventory for
	 * @param inventoryName the name of the inventory to open
	 */
	void openInventory(Player player, String inventoryName);

}
