package me.luckyraven.listener.inventory;

import lombok.RequiredArgsConstructor;
import me.luckyraven.Gangland;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

@ListenerHandler
@RequiredArgsConstructor
public class UniqueItemInteract implements Listener {

	private final Gangland gangland;

	@EventHandler
	public void onUniqueItemInteract(PlayerInteractEvent event) {
		ItemStack heldItem = event.getItem();

		if (heldItem == null) return;

		Player player = event.getPlayer();

		// check if the item has a unique item tag
		ItemBuilder itemBuilder = new ItemBuilder(heldItem);

		if (!itemBuilder.hasNBTTag("uniqueItem")) return;

		var uniqueItemKey = itemBuilder.getStringTagData("uniqueItem");
		var handler       = InventoryAddon.getUniqueItemHandler(uniqueItemKey);

		if (handler == null) return;
		if (!handler.isActionAllowed(event.getAction())) return;

		if (handler.getPermission() != null && !player.hasPermission(handler.getPermission())) return;

		InventoryAddon.openInventoryForPlayer(gangland, player, handler.getInventoryName());
		event.setCancelled(true);
	}

}
