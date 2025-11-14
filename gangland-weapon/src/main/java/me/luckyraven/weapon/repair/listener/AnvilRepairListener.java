package me.luckyraven.weapon.repair.listener;

import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.WeaponTag;
import me.luckyraven.weapon.repair.RepairManager;
import me.luckyraven.weapon.repair.item.RepairItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Handles weapon repair in anvils.
 */
public class AnvilRepairListener implements Listener {

	private final RepairManager repairManager;
	private final WeaponService weaponService;

	public AnvilRepairListener(@NotNull RepairManager repairManager, @NotNull WeaponService weaponService) {
		this.repairManager = repairManager;
		this.weaponService = weaponService;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onPrepareAnvil(PrepareAnvilEvent event) {
		AnvilInventory inventory = event.getInventory();

		ItemStack firstItem  = inventory.getItem(0);
		ItemStack secondItem = inventory.getItem(1);

		// Check if first item is a weapon and second is a repair item
		if (firstItem == null || secondItem == null) {
			return;
		}

		if (!isWeapon(firstItem) || !RepairItem.isRepairItem(secondItem)) {
			return;
		}

		// Validate repair item can be used
		if (!RepairItem.canUse(secondItem)) {
			event.setResult(null);
			return;
		}

		// Get weapon
		Weapon weapon = getWeaponFromItem(firstItem, event.getView().getPlayer());
		if (weapon == null) {
			event.setResult(null);
			return;
		}

		// Check if weapon needs repair
		if (weapon.getCurrentDurability() >= weapon.getDurability()) {
			event.setResult(null);
			return;
		}

		// Get repair item data
		RepairItem repairItemData = repairManager.getRepairItemManager().getRepairItem(secondItem);
		if (repairItemData == null) {
			event.setResult(null);
			return;
		}

		// Create preview (just show it will be repaired)
		Weapon      previewWeapon = weapon.clone();
		ItemStack   resultItem    = previewWeapon.buildItem();
		ItemBuilder resultBuilder = new ItemBuilder(resultItem);

		// Add preview lore
		java.util.List<String> lore = resultBuilder.getLore();
		if (lore == null) {
			lore = new java.util.ArrayList<>();
		}
		lore.add("");
		lore.add("§a§lRepair ready!");
		resultBuilder.setLore(lore);

		event.setResult(resultBuilder.build());

		// Set repair cost
		inventory.setRepairCost(1);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onAnvilClick(InventoryClickEvent event) {
		if (event.getInventory().getType() != InventoryType.ANVIL) {
			return;
		}

		if (event.getSlotType() != InventoryType.SlotType.RESULT) {
			return;
		}

		if (!(event.getWhoClicked() instanceof Player)) {
			return;
		}

		AnvilInventory inventory  = (AnvilInventory) event.getInventory();
		ItemStack      firstItem  = inventory.getItem(0);
		ItemStack      secondItem = inventory.getItem(1);
		ItemStack      result     = inventory.getItem(2);

		if (result == null || firstItem == null || secondItem == null) {
			return;
		}

		if (!isWeapon(firstItem) || !RepairItem.isRepairItem(secondItem)) {
			return;
		}

		Player player = (Player) event.getWhoClicked();

		// Get weapon
		Weapon weapon = getWeaponFromItem(firstItem, player);
		if (weapon == null) {
			event.setCancelled(true);
			player.sendMessage("§c✘ Invalid weapon!");
			return;
		}

		// Apply repair
		boolean success = repairManager.applyRepair(player, weapon, secondItem);
		if (!success) {
			event.setCancelled(true);
			return;
		}

		// Decrease repair item durability
		ItemStack newRepairItem = RepairItem.decreaseDurability(secondItem, 1);

		// Update items
		if (newRepairItem == null) {
			inventory.setItem(1, null); // Repair item broken
		} else {
			inventory.setItem(1, newRepairItem);
		}

		// Update result with repaired weapon
		ItemStack repairedWeapon = weapon.buildItem();
		inventory.setItem(2, repairedWeapon);

		// Remove first item
		inventory.setItem(0, null);
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
	@Nullable
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