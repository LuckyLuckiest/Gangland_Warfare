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

		// A transient copy: converting an item must not mint a registry entry (and a weapon table row) for an item
		// that may never be picked up. The instance registers itself the first time a player actually uses it.
		Weapon newWeapon = weaponService.createTransientWeapon(modifier);

		if (newWeapon == null) {
			return null;
		}

		ItemStack itemStack = newWeapon.buildItem();

		applyAttributes(itemStack, attributes);

		return itemStack;
	}
}
