package me.luckyraven.listener.inventory;

import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.item.wearable.Wearable;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles wearable armor equip/drop validation.
 *
 * <p>On equip (direct drag to armor slot or shift-click auto-equip): if the armor piece is a
 * registered {@link Wearable} with a permission node, the equip is blocked for players that lack that permission.
 *
 * <p>On drop: if the wearable is marked as non-droppable the drop event is cancelled.
 */
@ListenerHandler
@AutowireTarget({WearableService.class})
public class WearableEquipListener implements Listener {

	private final WearableService wearableService;

	public WearableEquipListener(WearableService wearableService) {
		this.wearableService = wearableService;
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onArmorEquip(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;

		ItemStack toEquip = resolveArmorBeingEquipped(event);
		if (!Wearable.isRegisteredWearable(toEquip)) return;

		String key = Wearable.getWearableKey(toEquip);

		if (key == null) return;

		Wearable wearable = wearableService.getWearable(key);

		if (wearable == null) return;

		// Permission check
		String permission = wearable.getPermission();
		if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
			event.setCancelled(true);
			player.sendMessage(ChatUtil.color("&cYou are not authorized to equip this armor."));
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onWearableDrop(PlayerDropItemEvent event) {
		ItemStack dropped = event.getItemDrop().getItemStack();
		if (!Wearable.isRegisteredWearable(dropped)) return;

		String key = Wearable.getWearableKey(dropped);

		if (key == null) return;

		Wearable wearable = wearableService.getWearable(key);

		if (wearable != null && !wearable.isDroppable()) {
			event.setCancelled(true);
		}
	}

	/**
	 * Determines which armor ItemStack is being equipped in the given click event, or returns {@code null} if the click
	 * does not result in an armor piece being equipped.
	 *
	 * <p>Covers two cases:
	 * <ul>
	 *   <li>Direct placement into an armor slot (cursor → armor slot).</li>
	 *   <li>Shift-click of an armor item from any other slot (auto-equip to the matching armor
	 *       slot).</li>
	 * </ul>
	 */
	private ItemStack resolveArmorBeingEquipped(InventoryClickEvent event) {
		// Case 1: player dragged cursor item directly onto an armor slot
		if (event.getSlotType() == SlotType.ARMOR) {
			InventoryAction action = event.getAction();
			if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE ||
				action == InventoryAction.PLACE_SOME || action == InventoryAction.SWAP_WITH_CURSOR) {
				ItemStack cursor = event.getCursor();
				return Wearable.isArmorItem(cursor) ? cursor : null;
			}
			return null;
		}

		// Case 2: shift-click on an armor item auto-equips it
		if (event.isShiftClick()) {
			ItemStack current = event.getCurrentItem();
			return Wearable.isArmorItem(current) ? current : null;
		}

		return null;
	}

}
