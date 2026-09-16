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
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.gangland.gadget.jetpack.JetpackService;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.gadget.jetpack.message.JetpackMessages;

/**
 * Handles jetpack equip/unequip via inventory interactions. When a jetpack is placed in the chestplate slot, enables
 * {@code allowFlight}. When removed, deactivates the session.
 *
 * <p>Review I3 (WS7 G4): the per-item permission denial message is sent from here — the two deliberate-interaction
 * paths a player actually equips a jetpack through (Car precedent: {@code CarInteractListener.java:59}) — never
 * from {@code JetpackService.activate}, which is also reached by join/dismount/undown and would otherwise spam the
 * message on every one of those.
 */
@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({JetpackService.class, JetpackAddon.class, JetpackMessages.class})
public class JetpackEquipListener implements Listener {

	private final JetpackService   jetpackService;
	private final JetpackAddon     jetpackAddon;
	private final JetpackMessages  jetpackMessages;

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

		ItemStack equipped = isDirectChestplateSlot ? event.getCursor() : event.getCurrentItem();
		warnIfNoPermission(player, equipped);

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

		warnIfNoPermission(player, item);

		jetpackService.scheduleChestplateCheck(player);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onToggleFly(PlayerToggleFlightEvent event) {
		if (!jetpackService.isActive(event.getPlayer())) return;

		event.setCancelled(true);
		event.getPlayer().setFlying(false);
	}

	/**
	 * Sends the module-owned denial message when {@code item} is a jetpack the player is not permitted to use.
	 * A no-op for a non-jetpack chestplate (nothing to warn about) or a jetpack the player is permitted to use.
	 */
	private void warnIfNoPermission(Player player, @Nullable ItemStack item) {
		String  id      = Jetpack.getJetpackId(item);
		Jetpack jetpack = id != null ? jetpackAddon.getJetpack(id) : null;

		if (jetpack != null && !player.hasPermission(jetpack.getPermission())) {
			player.sendMessage(jetpackMessages.noPermission());
		}
	}

	private boolean isCreativeOrSpectator(Player player) {
		return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
	}

}
