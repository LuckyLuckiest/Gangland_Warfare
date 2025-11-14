package me.luckyraven.weapon.repair.listener;

import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.WeaponTag;
import me.luckyraven.weapon.repair.RepairManager;
import me.luckyraven.weapon.repair.item.RepairItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Handles weapon repair in crafting tables.
 */
public class CraftingRepairListener implements Listener {

	private final RepairManager repairManager;
	private final WeaponService weaponService;

	public CraftingRepairListener(@NotNull RepairManager repairManager, @NotNull WeaponService weaponService) {
		this.repairManager = repairManager;
		this.weaponService = weaponService;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onPrepareCraft(PrepareItemCraftEvent event) {
		if (!(event.getInventory() instanceof CraftingInventory)) {
			return;
		}

		CraftingInventory inventory = (CraftingInventory) event.getInventory();
		ItemStack[]       matrix    = inventory.getMatrix();

		// Look for weapon + repair item combination
		ItemStack weaponItem = null;
		ItemStack repairItem = null;

		for (ItemStack item : matrix) {
			if (item == null || item.getType() == Material.AIR) {
				continue;
			}

			if (isWeapon(item)) {
				if (weaponItem != null) {
					// Multiple weapons, invalid
					inventory.setResult(null);
					return;
				}
				weaponItem = item;
			} else if (RepairItem.isRepairItem(item)) {
				if (repairItem != null) {
					// Multiple repair items, invalid
					inventory.setResult(null);
					return;
				}
				repairItem = item;
			} else {
				// Invalid item in crafting grid
				inventory.setResult(null);
				return;
			}
		}

		// Validate we have exactly 1 weapon and 1 repair item
		if (weaponItem == null || repairItem == null) {
			return; // Not a repair recipe
		}

		// Validate repair item can be used
		if (!RepairItem.canUse(repairItem)) {
			inventory.setResult(null);
			return;
		}

		// Get weapon data
		Weapon weapon = getWeaponFromItem(weaponItem, event.getView().getPlayer());
		if (weapon == null) {
			inventory.setResult(null);
			return;
		}

		// Check if weapon needs repair
		if (weapon.getCurrentDurability() >= weapon.getDurability()) {
			inventory.setResult(null);
			return;
		}

		// Calculate repair preview
		RepairItem repairItemData = repairManager.getRepairItemManager().getRepairItem(repairItem);
		if (repairItemData == null) {
			inventory.setResult(null);
			return;
		}

		// Create preview item (simulate repair)
		Weapon previewWeapon = weapon.clone();
		// We can't actually apply effects here, so just show the weapon will be repaired
		// The actual repair happens in the click event

		ItemStack   resultItem    = previewWeapon.buildItem();
		ItemBuilder resultBuilder = new ItemBuilder(resultItem);

		// Add preview lore
		java.util.List<String> lore = resultBuilder.getLore();
		if (lore == null) {
			lore = new java.util.ArrayList<>();
		}
		lore.add("");
		lore.add("§a§lClick to repair!");
		resultBuilder.setLore(lore);

		inventory.setResult(resultBuilder.build());
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onCraftClick(InventoryClickEvent event) {
		if (!(event.getInventory() instanceof CraftingInventory)) {
			return;
		}

		if (event.getSlotType() != InventoryType.SlotType.RESULT) {
			return;
		}

		if (!(event.getWhoClicked() instanceof Player)) {
			return;
		}

		CraftingInventory inventory = (CraftingInventory) event.getInventory();
		ItemStack         result    = inventory.getResult();

		if (result == null || result.getType() == Material.AIR) {
			return;
		}

		Player      player = (Player) event.getWhoClicked();
		ItemStack[] matrix = inventory.getMatrix();

		// Find weapon and repair item
		ItemStack weaponItem = null;
		ItemStack repairItem = null;
		int       weaponSlot = -1;
		int       repairSlot = -1;

		for (int i = 0; i < matrix.length; i++) {
			ItemStack item = matrix[i];
			if (item == null || item.getType() == Material.AIR) {
				continue;
			}

			if (isWeapon(item)) {
				weaponItem = item;
				weaponSlot = i;
			} else if (RepairItem.isRepairItem(item)) {
				repairItem = item;
				repairSlot = i;
			}
		}

		if (weaponItem == null || repairItem == null) {
			return; // Not a repair operation
		}

		// Get weapon data
		Weapon weapon = getWeaponFromItem(weaponItem, player);
		if (weapon == null) {
			event.setCancelled(true);
			player.sendMessage("§c✘ Invalid weapon!");
			return;
		}

		// Apply repair
		boolean success = repairManager.applyRepair(player, weapon, repairItem);
		if (!success) {
			event.setCancelled(true);
			return;
		}

		// Decrease repair item durability
		ItemStack newRepairItem = RepairItem.decreaseDurability(repairItem, 1);

		// Update items in crafting grid
		if (newRepairItem == null) {
			matrix[repairSlot] = null; // Repair item broken
		} else {
			matrix[repairSlot] = newRepairItem;
		}

		// Update weapon in grid
		ItemStack repairedWeapon = weapon.buildItem();
		matrix[weaponSlot] = repairedWeapon;

		// Set result to the repaired weapon
		inventory.setResult(repairedWeapon);

		// Update the matrix
		inventory.setMatrix(matrix);
	}

	/**
	 * Checks if an item is a weapon.
	 */
	private boolean isWeapon(@NotNull ItemStack item) {
		ItemBuilder builder = new ItemBuilder(item);
		return builder.hasNBTTag(Weapon.getTagProperName(WeaponTag.UUID));
	}

	/**
	 * Gets a weapon from an ItemStack.
	 */
	private Weapon getWeaponFromItem(@NotNull ItemStack item, @NotNull org.bukkit.entity.HumanEntity player) {
		if (!(player instanceof Player)) {
			return null;
		}

		ItemBuilder builder = new ItemBuilder(item);
		if (!builder.hasNBTTag(Weapon.getTagProperName(WeaponTag.UUID))) {
			return null;
		}

		String uuidStr = builder.getStringTagData(Weapon.getTagProperName(WeaponTag.UUID));
		String type    = builder.getStringTagData(Weapon.getTagProperName(WeaponTag.WEAPON));

		try {
			UUID uuid = UUID.fromString(uuidStr);
			return weaponService.getWeapon((Player) player, uuid, type);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}