package me.luckyraven.gadget.listener.jetpack;

import lombok.RequiredArgsConstructor;
import me.luckyraven.gadget.jetpack.JetpackService;
import me.luckyraven.item.wearable.Wearable;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles jetpack equip/unequip via inventory interactions. When a jetpack is placed in the chestplate slot, enables
 * {@code allowFlight}. When removed, deactivates the session.
 */
@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({WearableService.class, JetpackService.class})
public class JetpackEquipListener implements Listener {

	private final JavaPlugin      plugin;
	private final WearableService wearableService;
	private final JetpackService  jetpackService;

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (isCreativeOrSpectator(player)) return;

		// Only care about chestplate slot changes
		if (event.getSlotType() != InventoryType.SlotType.ARMOR) return;
		if (event.getSlot() != 38) return; // 38 = chestplate slot

		// Schedule check for next tick since the inventory hasn't updated yet
		player.getServer().getScheduler().runTask(plugin, () -> {
			ItemStack newChestplate = player.getInventory().getChestplate();
			Wearable  wearable      = newChestplate != null ? wearableService.resolveWearable(newChestplate) : null;

			if (wearable != null && wearable.isJetpack()) {
				jetpackService.activate(player, wearable);
			} else {
				if (jetpackService.isActive(player)) {
					jetpackService.deactivate(player);
				}
			}
		});
	}

	private boolean isCreativeOrSpectator(Player player) {
		return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
	}

}
