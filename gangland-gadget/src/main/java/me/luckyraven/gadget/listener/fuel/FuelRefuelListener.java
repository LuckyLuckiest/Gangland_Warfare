package me.luckyraven.gadget.listener.fuel;

import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.item.fuel.Fuel;
import me.luckyraven.util.item.fuel.FuelBar;
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
			if (tryRefuel(player, mainHand, offHand, fuelKey)) {
				event.setCancelled(true);
			}
			return;
		}

		// Case 2: Main hand = fuel item, off hand = fuel material
		if (Fuel.isFuelItem(mainHand) && !Fuel.isFuelItem(offHand)) {
			String fuelKey = Fuel.getFuelKey(mainHand);
			if (fuelKey == null) return;
			if (tryRefuel(player, offHand, mainHand, fuelKey)) {
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
			if (tryRefuelInInventory(player, cursor, clicked, fuelKey, event)) {
				event.setCancelled(true);
			}
			return;
		}

		// Cursor = fuel item, clicked = fuel material
		if (Fuel.isFuelItem(cursor) && !Fuel.isFuelItem(clicked)) {
			String fuelKey = Fuel.getFuelKey(cursor);
			if (fuelKey == null) return;
			if (tryRefuelInInventory(player, clicked, cursor, fuelKey, event)) {
				event.setCancelled(true);
			}
		}
	}

	/**
	 * Attempts to refuel a fuel item using a material item (world interaction).
	 *
	 * @return true if refueling occurred
	 */
	private boolean tryRefuel(Player player, ItemStack materialItem, ItemStack fuelItem, String fuelKey) {
		Fuel fuel = fuelService.getFuel(fuelKey);
		if (fuel == null) return false;

		Material expectedMaterial = fuel.getFuelMaterial().get();
		if (expectedMaterial == null || materialItem.getType() != expectedMaterial) return false;

		int current = Fuel.getCurrentFuel(fuelItem);
		int max     = Fuel.getMaxFuel(fuelItem);
		if (current >= max) {
			ChatUtil.sendActionBar(player, "&cFuel is already full!");
			return false;
		}

		// Consume one material item
		materialItem.setAmount(materialItem.getAmount() - 1);

		// Add fuel
		fuelService.addFuel(player, fuelKey, fuel.getFuelPerItem());

		// Play feedback
		player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);

		// Update action bar
		int newFuel = Math.min(max, current + fuel.getFuelPerItem());
		ChatUtil.sendActionBar(player, FuelBar.render(newFuel, max));
		return true;
	}

	/**
	 * Attempts to refuel via inventory click (cursor + clicked item interaction).
	 *
	 * @return true if refueling occurred
	 */
	private boolean tryRefuelInInventory(Player player, ItemStack materialItem, ItemStack fuelItem, String fuelKey,
	                                     InventoryClickEvent event) {
		Fuel fuel = fuelService.getFuel(fuelKey);
		if (fuel == null) return false;

		Material expectedMaterial = fuel.getFuelMaterial().get();
		if (expectedMaterial == null || materialItem.getType() != expectedMaterial) return false;

		int current = Fuel.getCurrentFuel(fuelItem);
		int max     = Fuel.getMaxFuel(fuelItem);
		if (current >= max) {
			ChatUtil.sendActionBar(player, "&cFuel is already full!");
			return false;
		}

		// Consume one material item from cursor/clicked
		materialItem.setAmount(materialItem.getAmount() - 1);

		// Add fuel to the item
		fuelService.addFuel(player, fuelKey, fuel.getFuelPerItem());

		// Play feedback
		player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);

		// Update action bar
		int newFuel = Math.min(max, current + fuel.getFuelPerItem());
		ChatUtil.sendActionBar(player, FuelBar.render(newFuel, max));
		return true;
	}

}
