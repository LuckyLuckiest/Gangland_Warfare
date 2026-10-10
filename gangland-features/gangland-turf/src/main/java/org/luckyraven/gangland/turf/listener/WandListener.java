package org.luckyraven.gangland.turf.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.turf.command.TurfPermissions;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.selection.Selection;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

@ListenerHandler
@RequiredArgsConstructor
public final class WandListener implements Listener {

	private final WandSelectionManager selections;
	private final TurfMessages         text;

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		Action action = event.getAction();
		if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		Block clicked = event.getClickedBlock();
		if (clicked == null) {
			return;
		}
		ItemStack item = event.getItem();
		if (item == null || !isWand(item)) {
			return;
		}

		Player player = event.getPlayer();
		if (!TurfPermissions.isAdmin(player)) {
			return;
		}
		event.setCancelled(true);

		boolean   first     = action == Action.LEFT_CLICK_BLOCK;
		Selection selection = selections.get(player);
		if (!selection.set(clicked.getLocation(), first)) {
			player.sendMessage(text.text("Turf.Selection_Other_World",
			                             "&cThat corner is in another world than your selection. Clear it with the other corner first."));
			return;
		}

		String corner = first
		                ? text.text("Turf.Corner_1", "&apos1")
		                : text.text("Turf.Corner_2", "&epos2");
		player.sendMessage(text.text("Turf.Corner_Set", "&7Turf %corner% &7set to &f(%x%, %y%, %z%)&7 in world &f%world%",
		                             "corner", corner,
		                             "x", String.valueOf(clicked.getX()),
		                             "y", String.valueOf(clicked.getY()),
		                             "z", String.valueOf(clicked.getZ()),
		                             "world", clicked.getWorld().getName()));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		selections.clear(event.getPlayer().getUniqueId());
	}

	private boolean isWand(ItemStack item) {
		return new ItemBuilder(item).hasNBTTag(WandSelectionManager.WAND_NBT_KEY);
	}
}
