package me.luckyraven.item.contract;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Contract used by {@code RepairListener} (in gangland-item) to ask the gangland-gadget repair system to open the
 * repair UI for a held weapon, without importing {@code RepairManager}/{@code RepairAnvilGui}/{@code WeaponService}.
 */
public interface RepairService {

	/**
	 * If {@code heldItem} is a repairable weapon owned by {@code player}, opens the repair UI and returns {@code true};
	 * otherwise returns {@code false}. The listener should {@code event.setCancelled(true)} on a {@code true} return.
	 *
	 * @param player the player attempting to open the anvil
	 * @param heldItem the item currently in the player's main hand
	 *
	 * @return {@code true} if the repair UI was opened
	 */
	boolean tryOpenRepairFor(Player player, ItemStack heldItem);

}
