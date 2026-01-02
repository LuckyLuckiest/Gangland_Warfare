package me.luckyraven.listener.inventory;

import lombok.RequiredArgsConstructor;
import me.luckyraven.Gangland;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.lootchest.LootChestWand;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@ListenerHandler
@RequiredArgsConstructor
public class LootChestWandHandler implements Listener {

	private final Gangland gangland;

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player    player   = event.getPlayer();
		ItemStack heldItem = player.getInventory().getItemInMainHand();

		if (!LootChestWand.isLootChestWand(heldItem)) return;

		Action action = event.getAction();
		Fill   fill   = new Fill(SettingAddon.getInventoryFillName(), SettingAddon.getInventoryFillItem());

		// Handle left click - open configuration menu
		if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
			event.setCancelled(true);
			LootChestWand wand = LootChestWand.getWand(heldItem, gangland);

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
		LootChestManager manager = gangland.getInitializer().getLootChestManager();
		if (manager.getChestAt(block.getLocation()).isPresent()) {
			// Cancel event to prevent vanilla chest from opening, LootChestListener will handle the rest
			event.setCancelled(true);
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
			player.sendMessage(ChatUtil.color("&cThis block type is not allowed for loot chests!"));
			player.sendMessage(ChatUtil.color("&7Allowed blocks: &e" + String.join(", ", allowedBlocks)));
			return;
		}

		LootChestWand wand = LootChestWand.getWand(heldItem, gangland);

		if (wand == null) return;

		// Check if wand is configured
		if (!LootChestWand.isConfigured(heldItem)) {
			player.sendMessage(ChatUtil.color("&cYour wand is not configured yet!"));
			player.sendMessage(ChatUtil.color("&7Opening configuration menu..."));
			wand.openConfigInventory(player, fill);
			return;
		}

		// Check if chest already exists at location
		if (manager.getChestAt(block.getLocation()).isPresent()) {
			player.sendMessage(ChatUtil.color("&cA loot chest already exists at this location!"));
			return;
		}

		// Create the loot chest
		wand.createLootChestFromWand(player, heldItem, block.getLocation());
	}

}