package org.luckyraven.gangland.item.contract;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.luckyraven.gangland.file.configuration.inventory.InventoryRuntimeContext;
import org.luckyraven.gangland.inventory.unique.UniqueItemHandler;

/**
 * Gangland-impl implementation of the {@link UniqueItemInteractionService} contract. Delegates to the injected
 * {@link InventoryRuntimeContext} for unique-item lookup and inventory opening so the moved {@code UniqueItemInteract}
 * listener does not need to import either of them.
 */
@RequiredArgsConstructor
public class GanglandUniqueItemInteractionService implements UniqueItemInteractionService {

	private final InventoryRuntimeContext runtimeContext;

	@Override
	public boolean tryHandleInteract(Player player, String uniqueItemKey, Action action) {
		if (uniqueItemKey == null || uniqueItemKey.isEmpty()) return false;

		UniqueItemHandler handler = runtimeContext.definitionStore().getUniqueItemHandler(uniqueItemKey);

		if (handler == null) return false;
		if (!handler.isActionAllowed(action)) return false;

		if (handler.permission() != null && !player.hasPermission(handler.permission())) return false;

		runtimeContext.openInventoryForPlayer(player, handler.inventoryName());
		return true;
	}

}
