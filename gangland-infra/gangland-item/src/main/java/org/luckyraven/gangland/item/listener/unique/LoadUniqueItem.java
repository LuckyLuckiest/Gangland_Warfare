package org.luckyraven.gangland.item.listener.unique;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.bean.listener.ListenerPriority;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.core.downed.PlayerUndownedEvent;
import org.luckyraven.gangland.item.contract.UniqueItemRegistry;
import org.luckyraven.gangland.item.event.PlayerItemInitEvent;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.item.unique.UniqueItemUtil;

import java.util.function.Predicate;

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
		Runnable grantJoinItems = () -> giveItems(player, UniqueItem::isAddOnJoin);

		if (event.isAsynchronous()) {
			Bukkit.getScheduler().runTask(plugin, grantJoinItems);
		} else {
			grantJoinItems.run();
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
		giveItems(event.getPlayer(), UniqueItem::isAddOnRespawn);
	}

	@EventHandler
	public void onPlayerUndowned(PlayerUndownedEvent event) {
		giveItems(event.getPlayer(), UniqueItem::isAddOnRespawn);
	}

	private void giveItems(Player player, Predicate<UniqueItem> gate) {
		var uniqueItems = uniqueItemAddon.getUniqueItems();

		for (var uniqueItem : uniqueItems.values()) {
			if (!gate.test(uniqueItem)) continue;
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
			if (!uniqueItem.matches(contents[i])) continue;

			inventory.setItem(i, null);

			if (!uniqueItem.isAllowDuplicates()) break;
		}
	}
}
