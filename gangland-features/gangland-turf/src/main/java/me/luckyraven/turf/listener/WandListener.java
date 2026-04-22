package me.luckyraven.turf.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.turf.selection.Selection;
import me.luckyraven.turf.selection.WandSelectionManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

@ListenerHandler
@RequiredArgsConstructor
public final class WandListener implements Listener {

	private final WandSelectionManager selections;

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
		if (!player.hasPermission(WandSelectionManager.ADMIN_PERMISSION)) {
			return;
		}
		event.setCancelled(true);

		boolean   first     = action == Action.LEFT_CLICK_BLOCK;
		Selection selection = selections.get(player);
		selection.set(clicked.getLocation(), first);

		String corner = first ? "&apos1" : "&epos2";
		player.sendMessage(ChatUtil.color(
				"&7Turf " + corner + " &7set to &f(" + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ() +
				")&7 in world &f" + clicked.getWorld().getName()));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		selections.clear(event.getPlayer().getUniqueId());
	}

	private boolean isWand(ItemStack item) {
		return new ItemBuilder(item).hasNBTTag(WandSelectionManager.WAND_NBT_KEY);
	}
}
