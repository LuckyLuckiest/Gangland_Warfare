package me.luckyraven.item.contract;

import lombok.RequiredArgsConstructor;
import me.luckyraven.Gangland;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import me.luckyraven.inventory.unique.UniqueItemHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

/**
 * Gangland-impl implementation of the {@link UniqueItemInteractionService} contract. Wraps {@link InventoryAddon}'s
 * static {@code getUniqueItemHandler} + {@code openInventoryForPlayer} so the moved {@code UniqueItemInteract} listener
 * does not need to import either of them.
 */
@RequiredArgsConstructor
public class GanglandUniqueItemInteractionService implements UniqueItemInteractionService {

	private final Gangland gangland;

	@Override
	public boolean tryHandleInteract(Player player, String uniqueItemKey, Action action) {
		if (uniqueItemKey == null || uniqueItemKey.isEmpty()) return false;

		UniqueItemHandler handler = InventoryAddon.getUniqueItemHandler(uniqueItemKey);

		if (handler == null) return false;
		if (!handler.isActionAllowed(action)) return false;

		if (handler.permission() != null && !player.hasPermission(handler.permission())) return false;

		InventoryAddon.openInventoryForPlayer(gangland, player, handler.inventoryName());
		return true;
	}

}
