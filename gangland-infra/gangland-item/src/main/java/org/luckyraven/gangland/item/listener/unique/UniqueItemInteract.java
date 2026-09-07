package org.luckyraven.gangland.item.listener.unique;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.item.contract.UniqueItemInteractionService;
import org.luckyraven.gangland.item.contract.UniqueItemRegistry;
import org.luckyraven.gangland.item.unique.UniqueItemUtil;

@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({UniqueItemRegistry.class, UniqueItemInteractionService.class})
public class UniqueItemInteract implements Listener {

	private final UniqueItemRegistry           uniqueItemAddon;
	private final UniqueItemInteractionService interactionService;

	@EventHandler
	public void onUniqueItemInteract(PlayerInteractEvent event) {
		ItemStack heldItem = event.getItem();

		if (heldItem == null) return;
		if (!UniqueItemUtil.isUniqueItem(heldItem)) return;

		ItemBuilder itemBuilder = new ItemBuilder(heldItem);

		var uniqueItemKey = itemBuilder.getStringTagData("uniqueItem");
		var uniqueItem    = uniqueItemAddon.getUniqueItem(uniqueItemKey);

		// check if the item is movable
		if (uniqueItem != null && !uniqueItem.isMovable()) {
			return;
		}

		boolean handled = interactionService.tryHandleInteract(event.getPlayer(), uniqueItemKey, event.getAction());

		if (handled) event.setCancelled(true);
	}

}
