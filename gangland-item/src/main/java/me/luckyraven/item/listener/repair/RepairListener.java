package me.luckyraven.item.listener.repair;

import me.luckyraven.item.contract.RepairService;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * When a player opens an anvil while holding a repairable weapon, the open is intercepted and replaced with the
 * configured custom repair UI. The actual repair logic and UI live behind {@link RepairService} so this listener has no
 * direct dependency on gangland-gadget or gangland-weapon.
 */
@ListenerHandler
@AutowireTarget({RepairService.class})
public class RepairListener implements Listener {

	private final RepairService repairService;

	public RepairListener(@NotNull RepairService repairService) {
		this.repairService = repairService;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onAnvilOpen(InventoryOpenEvent event) {
		if (event.getInventory().getType() != InventoryType.ANVIL) return;
		if (!(event.getPlayer() instanceof Player player)) return;

		ItemStack heldItem = player.getInventory().getItemInMainHand();

		if (repairService.tryOpenRepairFor(player, heldItem)) {
			event.setCancelled(true);
		}
	}

}
