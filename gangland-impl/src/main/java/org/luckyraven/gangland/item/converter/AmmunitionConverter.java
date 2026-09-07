package org.luckyraven.gangland.item.converter;

import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.luckyraven.gangland.item.ItemAttributes;
import org.luckyraven.gangland.weapon.ammo.Ammunition;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;

import java.util.Map;

@RequiredArgsConstructor
public class AmmunitionConverter extends ItemAttributes {

	private final AmmunitionManager ammunitionManager;

	@Override
	public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
		if (modifier == null || modifier.isBlank()) {
			return null;
		}

		Ammunition ammunition = ammunitionManager.getAmmunition(modifier);

		if (ammunition == null) {
			return null;
		}

		// clone the ammunition
		ItemStack itemStack = ammunition.buildItem();
		ItemMeta  meta      = itemStack.getItemMeta();

		applyAttributes(itemStack, attributes);

		if (meta != null && !meta.hasLore()) {
			meta.setLore(ammunition.getLore());

			itemStack.setItemMeta(meta);
		}

		return itemStack;
	}

}
