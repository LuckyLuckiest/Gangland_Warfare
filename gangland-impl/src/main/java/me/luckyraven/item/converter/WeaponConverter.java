package me.luckyraven.item.converter;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemAttributes;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import org.bukkit.inventory.ItemStack;

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
