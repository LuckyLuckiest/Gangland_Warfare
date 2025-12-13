package me.luckyraven.item.converter;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemAttributes;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

@RequiredArgsConstructor
public class AmmunitionConverter extends ItemAttributes {

	private final AmmunitionAddon ammunitionAddon;

	@Override
	public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
		if (modifier == null || modifier.isBlank()) {
			return null;
		}

		Ammunition ammunition = ammunitionAddon.getAmmunition(modifier);

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
