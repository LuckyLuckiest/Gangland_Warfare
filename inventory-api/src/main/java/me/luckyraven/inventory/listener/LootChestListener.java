package me.luckyraven.inventory.listener;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.inventory.loot.LootChestService;
import me.luckyraven.inventory.loot.data.LootChestSession;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.BiConsumer;

/**
 * Handles player interactions with loot chests
 */
@ListenerHandler
@RequiredArgsConstructor
public class LootChestListener implements Listener {

	private final LootChestService manager;

	@Setter
	private BiConsumer<Player, LootChestService.OpenResult> onOpenAttempt;

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

		Block block = event.getClickedBlock();
		if (block == null) return;

		manager.getChestAt(block.getLocation()).ifPresent(chestData -> {
			event.setCancelled(true);

			var player = event.getPlayer();
			var result = manager.tryOpenChest(player, chestData);

			// Handle result directly
			handleOpenResult(player, result);

			if (onOpenAttempt == null) return;

			onOpenAttempt.accept(player, result);
		});
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;

		manager.getActiveSession(player).ifPresent(session -> {
			if (session.getState() != LootChestSession.SessionState.LOOTING) return;
			// Check if player is taking an item (clicking on the chest inventory, not their own)
			if (event.getRawSlot() >= session.getInventory().getSize()) return;
			// Player clicked on the loot chest inventory
			if (!(event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir())) return;

			// Mark that an item was taken
			session.markItemTaken();
		});
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) return;

		manager.getActiveSession(player).ifPresent(session -> {
			if (session.getState() != LootChestSession.SessionState.LOOTING) return;

			manager.closeSession(player);
		});
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		manager.cancelSession(event.getPlayer());
	}

	private void handleOpenResult(Player player, LootChestService.OpenResult result) {
		switch (result) {
			case SUCCESS -> {
				// Chest opened successfully
			}
			case ALREADY_IN_SESSION -> player.sendMessage(ChatUtil.color("&cYou are already opening a chest!"));
			case ON_COOLDOWN -> player.sendMessage(ChatUtil.color("&cThis chest is on cooldown!"));
			case REQUIRES_LOCKPICK -> player.sendMessage(ChatUtil.color("&cYou need a lockpick to open this chest!"));
			case REQUIRES_KEY -> player.sendMessage(ChatUtil.color("&cYou need a key to open this chest!"));
			case NO_PERMISSION -> player.sendMessage(ChatUtil.color("&cYou don't have permission to open this chest!"));
			case INVALID_LOOT_TABLE -> player.sendMessage(ChatUtil.color("&cThis chest has an invalid loot table!"));
			case INVALID_CHEST -> player.sendMessage(ChatUtil.color("&cThis chest is invalid!"));
			case NO_ITEM_PROVIDER -> player.sendMessage(ChatUtil.color("&cLoot system is not configured properly!"));
			case ALREADY_LOOTED -> player.sendMessage(ChatUtil.color("&cThis chest has already been looted!"));
		}
	}

}


