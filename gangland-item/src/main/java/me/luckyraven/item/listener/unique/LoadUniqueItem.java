package me.luckyraven.item.listener.unique;

import me.luckyraven.core.autowire.AutowireTarget;
import me.luckyraven.core.downed.PlayerDownedEvent;
import me.luckyraven.core.downed.PlayerUndownedEvent;
import me.luckyraven.core.listener.ListenerHandler;
import me.luckyraven.core.listener.ListenerPriority;
import me.luckyraven.item.contract.UniqueItemRegistry;
import me.luckyraven.item.event.PlayerItemInitEvent;
import me.luckyraven.item.unique.UniqueItem;
import me.luckyraven.item.unique.UniqueItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

@ListenerHandler(priority = ListenerPriority.LOW)
@AutowireTarget({UniqueItemRegistry.class, JavaPlugin.class})
public class LoadUniqueItem implements Listener {

	private final UniqueItemRegistry uniqueItemAddon;
	private final JavaPlugin         plugin;

	public LoadUniqueItem(UniqueItemRegistry uniqueItemAddon, JavaPlugin plugin) {
		this.uniqueItemAddon = uniqueItemAddon;
		this.plugin          = plugin;
	}

	@EventHandler
	public void onJoinGiveItem(PlayerItemInitEvent event) {
		Player player = event.getPlayer();

		// PlayerItemInitEvent is fired async (bridged from the async UserDataInitEvent), so inventory
		// modifications must be hopped back to the main thread.
		Runnable giveItems = () -> {
			var uniqueItems = uniqueItemAddon.getUniqueItems();

			for (var uniqueItem : uniqueItems.values()) {
				if (!uniqueItem.isAddOnJoin()) continue;
				if (!uniqueItem.isAddToInventory()) continue;

				if (UniqueItemUtil.hasUniqueItem(player, uniqueItem) && !uniqueItem.isAllowDuplicates()) continue;

				uniqueItem.addItemToInventory(player);
			}
		};

		if (event.isAsynchronous()) {
			Bukkit.getScheduler().runTask(plugin, giveItems);
		} else {
			giveItems.run();
		}
	}

	@EventHandler
	public void onPlayerDowned(PlayerDownedEvent event) {
		Player player      = event.getPlayer();
		var    uniqueItems = uniqueItemAddon.getUniqueItems();

		for (var uniqueItem : uniqueItems.values()) {
			if (!uniqueItem.isDroppable()) continue;
			if (!UniqueItemUtil.hasUniqueItem(player, uniqueItem)) continue;

			removeItem(player, uniqueItem);
		}
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		giveRespawnItems(event.getPlayer());
	}

	@EventHandler
	public void onPlayerUndowned(PlayerUndownedEvent event) {
		giveRespawnItems(event.getPlayer());
	}

	private void giveRespawnItems(Player player) {
		var uniqueItems = uniqueItemAddon.getUniqueItems();

		for (var uniqueItem : uniqueItems.values()) {
			if (!uniqueItem.isAddOnRespawn()) continue;
			if (!uniqueItem.isAddToInventory()) continue;

			if (UniqueItemUtil.hasUniqueItem(player, uniqueItem) && !uniqueItem.isAllowDuplicates()) continue;

			uniqueItem.addItemToInventory(player);
		}
	}

	private void removeItem(Player player, UniqueItem uniqueItem) {
		PlayerInventory inventory = player.getInventory();
		ItemStack[]     contents  = inventory.getContents();

		for (int i = 0; i < contents.length; i++) {
			if (contents[i] == null) continue;
			if (uniqueItem.compareTo(contents[i]) == 0) continue;

			inventory.setItem(i, null);

			if (!uniqueItem.isAllowDuplicates()) break;
		}
	}
}
