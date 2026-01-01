package me.luckyraven.lootchest;

import lombok.RequiredArgsConstructor;
import me.luckyraven.inventory.loot.item.LootItemProvider;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

@RequiredArgsConstructor
public class GanglandLootItemProvider implements LootItemProvider {

	private final WeaponManager   weaponManager;
	private final AmmunitionAddon ammunitionAddon;
	private final UniqueItemAddon uniqueItemAddon;

	@Override
	public @Nullable ItemStack getWeapon(String weaponId) {
		var weapon = weaponManager.getWeapon(weaponId);
		return weapon != null ? weapon.buildItem() : null;
	}

	@Override
	public @Nullable ItemStack getAmmunition(String ammoId, int amount) {
		var ammo = ammunitionAddon.getAmmunition(ammoId);
		if (ammo == null) return null;

		ItemStack item      = ammo.buildItem(amount);
		int       maxAmount = Math.min(amount, item.getMaxStackSize());

		item.setAmount(maxAmount);

		return item;
	}

	@Override
	public @Nullable ItemStack getUniqueItem(String uniqueId) {
		var unique = uniqueItemAddon.getUniqueItem(uniqueId);
		return unique != null ? unique.buildItem() : null;
	}

	@Override
	public @Nullable ItemStack getRepairItem(String repairId, int amount) {
		// Implement based on your repair item system
		return null;
	}

	@Override
	public @Nullable ItemStack getConsumable(String consumableId, int amount) {
		// Implement based on your consumable system
		return null;
	}

	@Override
	public @Nullable ItemStack getMaterial(String materialId, int amount) {
		try {
			Material material  = Material.valueOf(materialId.toUpperCase());
			int      maxAmount = Math.min(amount, material.getMaxStackSize());

			return new ItemStack(material, maxAmount);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Override
	public @Nullable ItemStack getMiscItem(String miscId, int amount) {
		return getMaterial(miscId, amount);
	}

	@Override
	public boolean hasWeapon(String weaponId) {
		return weaponManager.getWeapon(weaponId) != null;
	}

	@Override
	public boolean hasAmmunition(String ammoId) {
		return ammunitionAddon.getAmmunition(ammoId) != null;
	}

	@Override
	public boolean hasUniqueItem(String uniqueId) {
		return uniqueItemAddon.getUniqueItem(uniqueId) != null;
	}

	@Override
	public boolean hasRepairItem(String repairId) {
		return false;
	}

}
