package me.luckyraven.gadget.listener.fuel;

import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.item.fuel.Fuel;
import me.luckyraven.item.fuel.FuelBar;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles right-click refueling: a player right-clicks a fuel item while holding the appropriate fuel material (e.g.
 * coal), or clicks a fuel item with the material on cursor in an inventory.
 */
@ListenerHandler
@AutowireTarget({FuelService.class})
public class FuelRefuelListener implements Listener {

	private final FuelService fuelService;

	public FuelRefuelListener(FuelService fuelService) {
		this.fuelService = fuelService;
	}

	/**
	 * Handles right-click in the world: main hand holds fuel material, off-hand holds fuel item (or vice versa).
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(PlayerInteractEvent event) {
		if (!event.getAction().name().contains("RIGHT_CLICK")) return;

		Player    player   = event.getPlayer();
		ItemStack mainHand = player.getInventory().getItemInMainHand();
		ItemStack offHand  = player.getInventory().getItemInOffHand();

		// Case 1: Main hand = fuel material, off hand = fuel item
		if (Fuel.isFuelItem(offHand) && !Fuel.isFuelItem(mainHand)) {
			String fuelKey = Fuel.getFuelKey(offHand);
			if (fuelKey == null) return;
			ItemStack updated = tryRefuel(player, mainHand, offHand, fuelKey);
			if (updated != null) {
				player.getInventory().setItemInOffHand(updated);
				event.setCancelled(true);
			}
			return;
		}

		// Case 2: Main hand = fuel item, off hand = fuel material
		if (Fuel.isFuelItem(mainHand) && !Fuel.isFuelItem(offHand)) {
			String fuelKey = Fuel.getFuelKey(mainHand);
			if (fuelKey == null) return;
			ItemStack updated = tryRefuel(player, offHand, mainHand, fuelKey);
			if (updated != null) {
				player.getInventory().setItemInMainHand(updated);
				event.setCancelled(true);
			}
		}
	}

	/**
	 * Handles inventory click: clicking a fuel item with a fuel material on cursor.
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;

		ItemStack cursor  = event.getCursor();
		ItemStack clicked = event.getCurrentItem();

		if (cursor == null || clicked == null) return;

		// Cursor = fuel material, clicked = fuel item
		if (Fuel.isFuelItem(clicked) && !Fuel.isFuelItem(cursor)) {
			String fuelKey = Fuel.getFuelKey(clicked);
			if (fuelKey == null) return;
			ItemStack updated = tryRefuelInInventory(player, cursor, clicked, fuelKey);
			if (updated != null) {
				event.setCurrentItem(updated);
				event.setCancelled(true);
			}
			return;
		}

		// Cursor = fuel item, clicked = fuel material
		if (Fuel.isFuelItem(cursor) && !Fuel.isFuelItem(clicked)) {
			String fuelKey = Fuel.getFuelKey(cursor);
			if (fuelKey == null) return;
			ItemStack updated = tryRefuelInInventory(player, clicked, cursor, fuelKey);
			if (updated != null) {
				event.getView().setCursor(updated);
				event.setCancelled(true);
			}
		}
	}

	/**
	 * Attempts to refuel a fuel item using a material item (world interaction).
	 *
	 * @return the updated fuel ItemStack, or {@code null} if refueling did not occur
	 */
	private ItemStack tryRefuel(Player player, ItemStack materialItem, ItemStack fuelItem, String fuelKey) {
		Fuel fuel = fuelService.getFuel(fuelKey);
		if (fuel == null) return null;

		Material expectedMaterial = fuel.getFuelMaterial().get();
		if (expectedMaterial == null || materialItem.getType() != expectedMaterial) return null;

		int current = Fuel.getCurrentFuel(fuelItem);
		int max     = Fuel.getMaxFuel(fuelItem);
		if (current >= max) {
			ChatUtil.sendActionBar(player, "&cFuel is already full!");
			return null;
		}

		// Consume one material item
		materialItem.setAmount(materialItem.getAmount() - 1);

		// Write fuel directly to the item
		int       newFuel = Math.min(max, current + fuel.getFuelPerItem());
		ItemStack updated = Fuel.setCurrentFuel(fuelItem, newFuel);

		// Play feedback
		player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
		ChatUtil.sendActionBar(player, FuelBar.render(newFuel, max));
		return updated;
	}

	/**
	 * Attempts to refuel via inventory click (cursor + clicked item interaction).
	 *
	 * @return the updated fuel ItemStack, or {@code null} if refueling did not occur
	 */
	private ItemStack tryRefuelInInventory(Player player, ItemStack materialItem, ItemStack fuelItem, String fuelKey) {
		Fuel fuel = fuelService.getFuel(fuelKey);
		if (fuel == null) return null;

		Material expectedMaterial = fuel.getFuelMaterial().get();
		if (expectedMaterial == null || materialItem.getType() != expectedMaterial) return null;

		int current = Fuel.getCurrentFuel(fuelItem);
		int max     = Fuel.getMaxFuel(fuelItem);
		if (current >= max) {
			ChatUtil.sendActionBar(player, "&cFuel is already full!");
			return null;
		}

		// Consume one material item from cursor/clicked
		materialItem.setAmount(materialItem.getAmount() - 1);

		// Write fuel directly to the item
		int       newFuel = Math.min(max, current + fuel.getFuelPerItem());
		ItemStack updated = Fuel.setCurrentFuel(fuelItem, newFuel);

		// Play feedback
		player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
		ChatUtil.sendActionBar(player, FuelBar.render(newFuel, max));
		return updated;
	}

}
