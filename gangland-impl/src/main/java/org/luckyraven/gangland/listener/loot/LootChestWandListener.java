package org.luckyraven.gangland.listener.loot;

import lombok.RequiredArgsConstructor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.core.utilities.ChatUtil;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.lootchest.LootChestManager;
import org.luckyraven.gangland.lootchest.LootChestWand;

import java.util.List;

@ListenerHandler
@RequiredArgsConstructor
public class LootChestWandListener implements Listener {

	private final Gangland         gangland;
	private final LootChestManager lootChestManager;

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player    player   = event.getPlayer();
		ItemStack heldItem = player.getInventory().getItemInMainHand();

		if (!LootChestWand.isLootChestWand(heldItem)) return;

		Action action = event.getAction();
		Fill   fill   = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());

		// Handle left click - open configuration menu
		if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
			event.setCancelled(true);
			LootChestWand wand = LootChestWand.getWand(heldItem, gangland, lootChestManager);

			if (wand != null) {
				wand.openConfigInventory(player, fill);
			}

			return;
		}

		// Handle right click - place loot chest
		if (action != Action.RIGHT_CLICK_BLOCK) return;

		Block block = event.getClickedBlock();
		if (block == null) return;

		// If a loot chest already exists here, cancel the event and let LootChestListener handle opening it
		if (lootChestManager.getChestAt(block.getLocation()).isPresent()) {
			// Cancel event to prevent vanilla chest from opening, LootChestListener will handle the rest
			event.setCancelled(true);
			return;
		}

		event.setCancelled(true);

		// Check if block is allowed
		List<String> allowedBlocks = Settings.getLootChestAllowedBlocks();
		if (allowedBlocks.isEmpty()) {
			allowedBlocks = List.of("CHEST", "TRAPPED_CHEST", "BARREL", "ENDER_CHEST");
		}

		boolean isAllowed = allowedBlocks.stream()
				.anyMatch(allowed -> block.getType().name().toUpperCase().contains(allowed.toUpperCase()));

		if (!isAllowed) {
			player.sendMessage(ChatUtil.color("&cThis block type is not allowed for loot chests!"));
			player.sendMessage(ChatUtil.color("&7Allowed blocks: &e" + String.join(", ", allowedBlocks)));
			return;
		}

		LootChestWand wand = LootChestWand.getWand(heldItem, gangland, lootChestManager);

		if (wand == null) return;

		// Check if wand is configured
		if (!LootChestWand.isConfigured(heldItem)) {
			player.sendMessage(ChatUtil.color("&cYour wand is not configured yet!"));
			player.sendMessage(ChatUtil.color("&7Opening configuration menu..."));
			wand.openConfigInventory(player, fill);
			return;
		}

		// Check if chest already exists at location
		if (lootChestManager.getChestAt(block.getLocation()).isPresent()) {
			player.sendMessage(ChatUtil.color("&cA loot chest already exists at this location!"));
			return;
		}

		// Create the loot chest
		wand.createLootChestFromWand(player, heldItem, block.getLocation());
	}

}