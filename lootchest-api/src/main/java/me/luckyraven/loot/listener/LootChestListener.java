package me.luckyraven.loot.listener;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.loot.LootChestService;
import me.luckyraven.loot.data.LootChestData;
import me.luckyraven.loot.data.LootChestSession;
import me.luckyraven.util.configuration.SoundConfiguration;
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

import java.util.Optional;
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

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

		Block block = event.getClickedBlock();
		if (block == null) return;

		Optional<LootChestData> chestOptional = manager.getChestAt(block.getLocation());
		if (chestOptional.isEmpty()) return;

		// MUST cancel the event first to prevent the vanilla chest from opening
		event.setCancelled(true);

		var chestData = chestOptional.get();
		var player    = event.getPlayer();

		// Schedule the chest opening for the next tick to ensure the event is fully canceled
		manager.getPlugin().getServer().getScheduler().runTask(manager.getPlugin(), () -> {
			var result = manager.tryOpenChest(player, chestData);

			// handle the results internally with custom messages
			if (onOpenAttempt != null) {
				onOpenAttempt.accept(player, result);
				return;
			}

			// Handle result directly
			handleOpenResult(player, result, chestData);
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

			// Sync inventory state immediately after the click is processed
			manager.getPlugin()
				   .getServer()
				   .getScheduler()
				   .runTask(manager.getPlugin(), session::syncInventoryToChestData);
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

	private void handleOpenResult(Player player, LootChestService.OpenResult result, LootChestData chestData) {
		switch (result) {
			case SUCCESS -> {
				// Chest opened successfully
			}
			case CRACKING_STARTED -> player.sendMessage(
					ChatUtil.color("&eCracking the chest... Complete the minigame!"));
			case ALREADY_IN_SESSION -> player.sendMessage(ChatUtil.color("&cYou are already opening a chest!"));
			case ON_COOLDOWN -> {
				long remaining = chestData.getRemainingCooldownSeconds();
				player.sendMessage(
						ChatUtil.color("&cThis chest is empty and on cooldown! &7(" + formatTime(remaining) + ")"));
			}
			case REQUIRES_LOCKPICK -> {
				playLockedSound(player);
				player.sendMessage(ChatUtil.color("&cYou need a lockpick to open this chest!"));
			}
			case REQUIRES_KEY -> {
				playLockedSound(player);
				player.sendMessage(ChatUtil.color("&cYou need a key to open this chest!"));
			}
			case NO_PERMISSION -> {
				playLockedSound(player);
				player.sendMessage(ChatUtil.color("&cYou don't have permission to open this chest!"));
			}
			case INVALID_LOOT_TABLE -> player.sendMessage(ChatUtil.color("&cThis chest has an invalid loot table!"));
			case INVALID_CHEST -> player.sendMessage(ChatUtil.color("&cThis chest is invalid!"));
			case NO_ITEM_PROVIDER -> player.sendMessage(ChatUtil.color("&cLoot system is not configured properly!"));
			case ALREADY_LOOTED -> player.sendMessage(ChatUtil.color("&cThis chest has already been looted!"));
		}
	}

	private void playLockedSound(Player player) {
		String lockedSound = manager.getConfig().getLockedSound();

		if (!(lockedSound != null && !lockedSound.isEmpty())) return;

		var soundConfig = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, lockedSound, 1.0f, 1.0f);

		soundConfig.playSound(player);
	}

	private String formatTime(long seconds) {
		if (seconds < 60) {
			return seconds + "s";
		} else if (seconds < 3600) {
			long minutes = seconds / 60;
			long secs    = seconds % 60;
			return minutes + "m " + secs + "s";
		} else {
			long hours   = seconds / 3600;
			long minutes = (seconds % 3600) / 60;
			return hours + "h " + minutes + "m";
		}
	}

}
