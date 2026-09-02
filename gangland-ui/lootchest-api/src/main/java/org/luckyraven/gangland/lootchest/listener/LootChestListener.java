package org.luckyraven.gangland.lootchest.listener;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.TimeUtil;
import org.luckyraven.keystone.util.messages.TimeMessagesProvider;
import org.luckyraven.gangland.lootchest.LootChestService;
import org.luckyraven.gangland.lootchest.config.LootChestMessagesProvider;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.data.LootChestSession;

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

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onChestIconPickup(EntityPickupItemEvent event) {
		if (!manager.getCooldownManager().isChestIcon(event.getItem())) return;

		event.setCancelled(true);
	}

	private void handleOpenResult(Player player, LootChestService.OpenResult result, LootChestData chestData) {
		LootChestMessagesProvider msg = manager.getMessagesProvider();

		switch (result) {
			case SUCCESS -> {
				// Chest opened successfully
			}
			case CRACKING_STARTED -> {
				player.sendMessage(msg != null ?
				                   msg.getCrackingStarted() :
				                   ChatUtil.color("&eCracking the chest... Complete the minigame!"));
			}
			case ALREADY_IN_SESSION -> {
				player.sendMessage(
						msg != null ? msg.getAlreadyInSession() : ChatUtil.color("&cYou are already opening a chest!"));
			}
			case ON_COOLDOWN -> {
				long                 remaining     = chestData.getRemainingCooldownSeconds();
				TimeMessagesProvider timeUnits     = msg != null ? msg.getTimeMessages() : new DefaultTimeMessages();
				String               formattedTime = TimeUtil.formatTime(remaining, true, timeUnits);
				player.sendMessage(msg != null ?
				                   msg.getOnCooldown(formattedTime) :
				                   ChatUtil.color("&cThis chest is empty and on cooldown! &7(" + formattedTime + ")"));
			}
			case REQUIRES_LOCKPICK -> {
				playLockedSound(player);
				player.sendMessage(msg != null ?
				                   msg.getRequiresLockpick() :
				                   ChatUtil.color("&cYou need a lockpick to open this chest!"));
			}
			case REQUIRES_KEY -> {
				playLockedSound(player);
				player.sendMessage(
						msg != null ? msg.getRequiresKey() : ChatUtil.color("&cYou need a key to open this chest!"));
			}
			case NO_PERMISSION -> {
				playLockedSound(player);
				player.sendMessage(msg != null ?
				                   msg.getNoPermission() :
				                   ChatUtil.color("&cYou don't have permission to open this chest!"));
			}
			case INVALID_LOOT_TABLE -> {
				player.sendMessage(msg != null ?
				                   msg.getInvalidLootTable() :
				                   ChatUtil.color("&cThis chest has an invalid loot table!"));
			}
			case INVALID_CHEST -> {
				player.sendMessage(msg != null ? msg.getInvalidChest() : ChatUtil.color("&cThis chest is invalid!"));
			}
			case NO_ITEM_PARSER -> {
				player.sendMessage(msg != null ?
				                   msg.getNoItemProvider() :
				                   ChatUtil.color("&cLoot system is not configured properly!"));
			}
			case ALREADY_LOOTED -> {
				player.sendMessage(
						msg != null ? msg.getAlreadyLooted() : ChatUtil.color("&cThis chest has already been looted!"));
			}
		}
	}

	private void playLockedSound(Player player) {
		String lockedSound = manager.getConfig().getLockedSound();

		if (!(lockedSound != null && !lockedSound.isEmpty())) return;

		var soundConfig = new SoundEffect(SoundEffect.SoundType.VANILLA, lockedSound, 1.0f, 1.0f);

		soundConfig.playSound(player);
	}

	/**
	 * Fallback time-unit labels used when no {@link LootChestMessagesProvider} is set.
	 */
	private static class DefaultTimeMessages implements TimeMessagesProvider {

		@Override
		public String getYear() {
			return "y";
		}

		@Override
		public String getWeek() {
			return "w";
		}

		@Override
		public String getDay() {
			return "d";
		}

		@Override
		public String getHour() {
			return "h";
		}

		@Override
		public String getMinute() {
			return "m";
		}

		@Override
		public String getSecond() {
			return "s";
		}
	}

}
