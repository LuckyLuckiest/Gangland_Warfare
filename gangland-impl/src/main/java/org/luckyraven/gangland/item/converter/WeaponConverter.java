package org.luckyraven.gangland.item.converter;

import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.item.ItemAttributes;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;

import java.util.Map;

@RequiredArgsConstructor
public class WeaponConverter extends ItemAttributes {

	private final WeaponService weaponService;

	@Override
	public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
		if (modifier == null || modifier.isBlank()) {
			return null;
		}

		Weapon weapon = weaponService.getWeapon(modifier);

		if (weapon == null) {
			return null;
		}

		// clone the weapon
		Weapon    newWeapon = weapon.clone();
		ItemStack itemStack = newWeapon.buildItem();

		applyAttributes(itemStack, attributes);

		return itemStack;
	}
}
