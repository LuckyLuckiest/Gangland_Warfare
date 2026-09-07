package org.luckyraven.gangland.inventory.handler;

import org.bukkit.entity.Player;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.inventory.InventoryHandler;

/**
 * Handles {@code OnClose} slot events — a "close button" that runs the configured command then closes the player's
 * current inventory.
 */
public class CloseSlotHandler extends AbstractCommandSlotHandler {

	@Override
	protected void onSlotAction(Player player, InventoryHandler inv, ItemBuilder builder) {
		player.closeInventory();
	}

}
