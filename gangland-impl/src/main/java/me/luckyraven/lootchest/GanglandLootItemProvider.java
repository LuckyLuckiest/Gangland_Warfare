package me.luckyraven.lootchest;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.loot.item.LootItemProvider;
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
	@Nullable
	public ItemStack getWeapon(String weaponId) {
		var weapon = weaponManager.getWeapon(weaponId);

		return weapon != null ? weapon.buildItem() : null;
	}

	@Override
	@Nullable
	public ItemStack getAmmunition(String ammoId, int amount) {
		var ammo = ammunitionAddon.getAmmunition(ammoId);
		if (ammo == null) return null;

		var item      = ammo.buildItem(amount);
		var maxAmount = Math.min(amount, item.getMaxStackSize());

		item.setAmount(maxAmount);

		return item;
	}

	@Override
	@Nullable
	public ItemStack getUniqueItem(String uniqueId) {
		var unique = uniqueItemAddon.getUniqueItem(uniqueId);

		return unique != null ? unique.buildItem() : null;
	}

	@Override
	@Nullable
	public ItemStack getRepairItem(String repairId, int amount) {
		// Implement based on your repair item system
		return null;
	}

	@Override
	@Nullable
	public ItemStack getConsumable(String consumableId, int amount) {
		// Implement based on your consumable system
		return null;
	}

	@Override
	@Nullable
	public ItemStack getMaterial(String materialId, int amount) {
		try {
			var material  = Material.valueOf(materialId.toUpperCase());
			var maxAmount = Math.min(amount, material.getMaxStackSize());

			return new ItemStack(material, maxAmount);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Override
	@Nullable
	public ItemStack getMiscItem(String miscId, int amount) {
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
