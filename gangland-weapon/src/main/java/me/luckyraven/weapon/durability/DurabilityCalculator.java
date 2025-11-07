package me.luckyraven.weapon.durability;

import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.Weapon;

public class DurabilityCalculator {

	private final Weapon weapon;

	public DurabilityCalculator(Weapon weapon) {
		this.weapon = weapon;
	}

	public void setDurability(ItemBuilder itemBuilder, short durability) {
		short maxDurability = itemBuilder.getItemMaxDurability();

		weapon.setCurrentDurability((short) Math.max(maxDurability, durability));

		short newDamageValue = getWeaponDurability(itemBuilder);

		itemBuilder.setDurability(newDamageValue);
	}

	public short getWeaponDurability(ItemBuilder itemBuilder) {
		double weaponMaxDurability = weapon.getDurability();
		double itemMaxDurability   = itemBuilder.getItemMaxDurability();

		double weaponDurabilityLost = weaponMaxDurability - weapon.getCurrentDurability();

		double scale = itemMaxDurability / weaponMaxDurability;

		double itemDamageValue = Math.floor(weaponDurabilityLost * scale);

		return (short) itemDamageValue;
	}

	/**
	 * Converts the item's current damage value back to weapon durability.
	 * <p>Used when loading weapon data from an existing item.
	 *
	 * @param itemBuilder The item to read damage from
	 *
	 * @return The weapon's current durability
	 */
	public short calculateWeaponDurabilityFromItem(ItemBuilder itemBuilder) {
		double weaponMaxDurability = weapon.getDurability();
		double itemMaxDurability   = itemBuilder.getItemMaxDurability();
		double itemCurrentDamage   = itemBuilder.getItemDamagedDurability();

		// Calculate the scale factor
		double scale = itemMaxDurability / weaponMaxDurability;

		// Convert item damage back to weapon durability lost
		double weaponDurabilityLost = itemCurrentDamage / scale;

		// Calculate current weapon durability
		double weaponCurrentDurability = weaponMaxDurability - weaponDurabilityLost;

		// Ensure it is within valid bounds
		return (short) Math.max(0, Math.min(weaponMaxDurability, weaponCurrentDurability));
	}

}
