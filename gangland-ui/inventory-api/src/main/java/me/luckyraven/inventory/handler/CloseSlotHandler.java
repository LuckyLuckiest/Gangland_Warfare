package me.luckyraven.inventory.handler;

import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.entity.Player;

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
