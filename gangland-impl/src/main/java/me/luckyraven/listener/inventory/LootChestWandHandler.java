package me.luckyraven.listener.inventory;

import lombok.RequiredArgsConstructor;
import me.luckyraven.Gangland;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.lootchest.LootChestWand;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ListenerHandler
@RequiredArgsConstructor
public class LootChestWandHandler implements Listener {

	private final Gangland gangland;

	// Track players in configuration mode
	private final Map<UUID, ConfigSession> configSessions = new HashMap<>();

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player    player   = event.getPlayer();
		ItemStack heldItem = player.getInventory().getItemInMainHand();

		if (!LootChestWand.isLootChestWand(heldItem)) return;

		Action action = event.getAction();

		// Handle left click - open configuration menu
		if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
			event.setCancelled(true);
			LootChestWand wand = LootChestWand.getWand(heldItem, gangland);

			if (wand != null) {
				wand.openConfigInventory(player);
			}

			return;
		}

		// Handle right click - place loot chest
		if (action != Action.RIGHT_CLICK_BLOCK) return;

		Block block = event.getClickedBlock();
		if (block == null) return;

		// If a loot chest already exists here, let LootChestListener handle opening it
		LootChestManager manager = gangland.getInitializer().getLootChestManager();
		if (manager.getChestAt(block.getLocation()).isPresent()) {
			// Don't cancel - let the LootChestListener handle it
			return;
		}

		event.setCancelled(true);

		// Check if block is allowed
		List<String> allowedBlocks = SettingAddon.getLootChestAllowedBlocks();
		if (allowedBlocks.isEmpty()) {
			allowedBlocks = List.of("CHEST", "TRAPPED_CHEST", "BARREL", "ENDER_CHEST");
		}

		boolean isAllowed = allowedBlocks.stream()
				.anyMatch(allowed -> block.getType().name().toUpperCase().contains(allowed.toUpperCase()));

		if (!isAllowed) {
			player.sendMessage("§cThis block type is not allowed for loot chests!");
			player.sendMessage("§7Allowed blocks: §e" + String.join(", ", allowedBlocks));
			return;
		}

		LootChestWand wand = LootChestWand.getWand(heldItem, gangland);

		if (wand == null) return;

		// Check if wand is configured
		if (!LootChestWand.isConfigured(heldItem)) {
			player.sendMessage("§cYour wand is not configured yet!");
			player.sendMessage("§7Opening configuration menu...");
			wand.openConfigInventory(player);
			return;
		}

		// Check if chest already exists at location
		if (manager.getChestAt(block.getLocation()).isPresent()) {
			player.sendMessage("§cA loot chest already exists at this location!");
			return;
		}

		// Create the loot chest
		wand.createLootChestFromWand(player, heldItem, block.getLocation());
	}

	// Inner class for tracking configuration sessions
	private static class ConfigSession {
		String lootTableId   = "";
		String tierId        = "";
		int    inventorySize = 27;
		long   respawnTime   = 300;
		String displayName   = "§eLoot Chest";
	}
}