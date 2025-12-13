package me.luckyraven.listener.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.UserDataInitEvent;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.util.item.unique.UniqueItem;
import me.luckyraven.util.item.unique.UniqueItemUtil;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.listener.ListenerPriority;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

@ListenerHandler(priority = ListenerPriority.LOW)
public class LoadUniqueItem implements Listener {

	private final UniqueItemAddon uniqueItemAddon;

	public LoadUniqueItem(Gangland gangland) {
		this.uniqueItemAddon = gangland.getInitializer().getUniqueItemAddon();
	}

	@EventHandler
	public void onJoinGiveItem(UserDataInitEvent event) {
		Player player      = event.getPlayer();
		var    uniqueItems = uniqueItemAddon.getUniqueItems();

		for (var uniqueItem : uniqueItems.values()) {
			if (!uniqueItem.isAddOnJoin()) continue;
			if (!uniqueItem.isAddToInventory()) continue;

			if (UniqueItemUtil.hasUniqueItem(player, uniqueItem) && !uniqueItem.isAllowDuplicates()) continue;

			uniqueItem.addItemToInventory(player);
		}
	}

	@EventHandler
	public void beforePlayerDeath(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;

		double remainingHealth = player.getHealth() - event.getFinalDamage();

		if (remainingHealth > 0) return;

		var uniqueItems = uniqueItemAddon.getUniqueItems();

		for (var uniqueItem : uniqueItems.values()) {
			if (!uniqueItem.isDroppable()) continue;
			if (!UniqueItemUtil.hasUniqueItem(player, uniqueItem)) continue;

			removeItem(player, uniqueItem);
		}
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		Player player      = event.getPlayer();
		var    uniqueItems = uniqueItemAddon.getUniqueItems();

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

