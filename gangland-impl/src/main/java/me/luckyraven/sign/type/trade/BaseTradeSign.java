package me.luckyraven.sign.type.trade;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.sign.type.Sign;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

@Getter
@RequiredArgsConstructor
public abstract class BaseTradeSign implements Sign {

	private final WeaponService   weaponService;
	private final AmmunitionAddon ammunitionAddon;

	protected ItemStack getRequiredItem(String itemName) {
		Collection<Weapon> values = weaponService.getWeapons().values();

		// check if it is a weapon
		ItemStack itemStack = values.stream()
									.filter(w -> w.getName().equalsIgnoreCase(itemName))
									.findFirst()
									.map(Weapon::buildItem)
									.orElse(null);

		// check if it is ammunition
		if (itemStack == null) {
			Collection<String> ammunition = ammunitionAddon.getAmmunitionKeys();

			itemStack = ammunition.stream()
								  .map(ammunitionAddon::getAmmunition)
								  .filter(ammo -> ammo.getName().equalsIgnoreCase(itemName))
								  .findFirst()
								  .map(Ammunition::buildItem)
								  .orElse(null);
		}

		// last hope check if it is an item
		if (itemStack == null) {
			itemStack = Arrays.stream(XMaterial.values())
							  .map(XMaterial::get)
							  .filter(Objects::nonNull)
							  .filter(material -> material.name().equalsIgnoreCase(itemName))
							  .findFirst()
							  .map(ItemStack::new)
							  .orElse(null);
		}

		return itemStack;
	}

}
