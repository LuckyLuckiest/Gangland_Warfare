package org.luckyraven.gangland.copsncrooks.listener.setup;

import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.luckyraven.gangland.copsncrooks.setup.SetupMessages;
import org.luckyraven.gangland.copsncrooks.setup.SetupOutline;
import org.luckyraven.gangland.copsncrooks.setup.SetupSelections;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.Map;

/**
 * The setup wand: left click on a block is pos1, right click is pos2 (shape of the turf wand listener). The selection
 * is dropped on quit and on a world change. Without the setup permission, or with any other item, the click is left
 * alone.
 *
 * @since 0.16.0
 */
@ListenerHandler
@RequiredArgsConstructor
public final class SetupWandListener implements Listener {

	private final SetupSelections selections;
	private final SetupOutline    outline;
	private final SetupMessages   messages;

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		Action action = event.getAction();
		if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
		if (event.getHand() == EquipmentSlot.OFF_HAND) return;

		Block clicked = event.getClickedBlock();
		if (clicked == null || !SetupSelections.isWand(event.getItem())) return;

		Player player = event.getPlayer();
		if (!player.hasPermission(SetupSelections.PERMISSION)) return;
		event.setCancelled(true);

		Location at    = clicked.getLocation();
		boolean  first = action == Action.LEFT_CLICK_BLOCK;
		selections.get(player.getUniqueId()).set(at, first);
		outline.ensureRunning();

		String world = at.getWorld() == null ? "?" : at.getWorld().getName();
		player.sendMessage(messages.format(SetupMessages.Key.POS_SET,
		                                   Map.of("corner", first ? "pos1" : "pos2",
		                                          "x", String.valueOf(at.getBlockX()),
		                                          "y", String.valueOf(at.getBlockY()),
		                                          "z", String.valueOf(at.getBlockZ()),
		                                          "world", world)));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		selections.clear(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onWorldChange(PlayerChangedWorldEvent event) {
		selections.clear(event.getPlayer().getUniqueId());
	}
}
