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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
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

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

		Block block = event.getClickedBlock();
		if (block == null) return;

		manager.getChestAt(block.getLocation()).ifPresent(chestData -> {
			event.setCancelled(true);

			var player = event.getPlayer();
			var result = manager.tryOpenChest(player, chestData);

			if (onOpenAttempt == null) return;

			onOpenAttempt.accept(player, result);
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
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();

		manager.getActiveSession(player).ifPresent(session -> {
			if (session.getState() != LootChestSession.SessionState.COUNTDOWN) return;
			if (!(event.getTo() != null && event.getFrom().distanceSquared(event.getTo()) > 0.01)) return;

			manager.cancelSession(player);
			player.sendMessage(ChatUtil.color("&cChest opening cancelled - you moved!"));
		});
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		// Only cancel the player's session, not the chest cooldown
		// The chest cooldown continues even when the player leaves
		manager.cancelSession(event.getPlayer());
	}

}


