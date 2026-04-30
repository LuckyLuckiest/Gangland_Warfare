package org.luckyraven.gangland.gadget.listener.jetpack;

import lombok.RequiredArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.core.bean.autowire.AutowireTarget;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.gadget.jetpack.JetpackService;

/**
 * Handles jetpack equip/unequip via inventory interactions. When a jetpack is placed in the chestplate slot, enables
 * {@code allowFlight}. When removed, deactivates the session.
 */
@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({JetpackService.class})
public class JetpackEquipListener implements Listener {

	private final JetpackService jetpackService;

	@EventHandler(priority = EventPriority.MONITOR)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (isCreativeOrSpectator(player)) return;

		InventoryAction action = event.getAction();

		// Drag/drop directly onto the chestplate slot
		boolean isDirectChestplateSlot = event.getSlotType() == InventoryType.SlotType.ARMOR && event.getSlot() == 38;
		// Shift-click a chestplate item in the inventory to auto-equip it
		boolean isMoveToChestplate = action == InventoryAction.MOVE_TO_OTHER_INVENTORY &&
		                             event.getCurrentItem() != null &&
		                             event.getCurrentItem().getType().name().endsWith("_CHESTPLATE");

		if (!isDirectChestplateSlot && !isMoveToChestplate) return;

		jetpackService.scheduleChestplateCheck(player);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onInteract(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;

		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

		Player    player = event.getPlayer();
		ItemStack item   = event.getItem();

		if (isCreativeOrSpectator(player)) return;
		if (item == null || !item.getType().name().endsWith("_CHESTPLATE")) return;

		jetpackService.scheduleChestplateCheck(player);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onToggleFly(PlayerToggleFlightEvent event) {
		if (!jetpackService.isActive(event.getPlayer())) return;

		event.setCancelled(true);
		event.getPlayer().setFlying(false);
	}

	private boolean isCreativeOrSpectator(Player player) {
		return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
	}

}
