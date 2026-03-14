package me.luckyraven.sign.type.trade;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.sign.aspect.ItemTransferAspect.ItemSimilarityChecker;
import me.luckyraven.sign.type.Sign;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.item.unique.UniqueItem;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public abstract class BaseTradeSign implements Sign {

	private final WeaponService   weaponService;
	private final AmmunitionAddon ammunitionAddon;

	protected ItemStack getUniqueOrMaterialItem(String itemName, UniqueItemAddon uniqueItemAddon) {
		// check unique items first
		UniqueItem uniqueItem = uniqueItemAddon.getUniqueItem(itemName);

		if (uniqueItem != null) return uniqueItem.buildItem();

		// fall back to vanilla material
		return Arrays.stream(XMaterial.values())
				.map(XMaterial::get)
				.filter(Objects::nonNull)
				.filter(material -> material.name().equalsIgnoreCase(itemName))
				.findFirst()
				.map(ItemStack::new)
				.orElse(null);
	}

	protected ItemStack getWeaponItem(String weaponName) {
		return weaponService.getWeapons()
							.values()
				.stream()
				.filter(w -> w.getName().equalsIgnoreCase(weaponName))
				.findFirst()
				.map(template -> new Weapon(UUID.randomUUID(), template).buildItem())
				.orElse(null);
	}

	protected ItemStack getAmmoItem(String ammoKey) {
		return ammunitionAddon.getAmmunitionKeys()
				.stream()
				.filter(k -> k.equalsIgnoreCase(ammoKey))
				.map(ammunitionAddon::getAmmunition)
				.filter(Objects::nonNull)
				.findFirst()
				.map(Ammunition::buildItem)
				.orElse(null);
	}

	protected ItemSimilarityChecker weaponSimilarityChecker() {
		return (player, a, b) -> {
			if (a.getType() != b.getType()) return false;
			if (!weaponService.isWeapon(a) || !weaponService.isWeapon(b)) return a.isSimilar(b);
			Weapon w1 = weaponService.validateAndGetWeapon(player, a);
			Weapon w2 = weaponService.validateAndGetWeapon(player, b);
			return w1 != null && w2 != null && weaponService.compare(w1, w2) == 0;
		};
	}

	protected ItemSimilarityChecker ammoSimilarityChecker() {
		return (player, a, b) -> {
			if (a.getType() != b.getType()) return false;
			if (!Ammunition.isAmmunition(a) || !Ammunition.isAmmunition(b)) return a.isSimilar(b);
			Ammunition ammo1 = ammunitionAddon.getAmmunition(new ItemBuilder(a).getStringTagData("ammo"));
			Ammunition ammo2 = ammunitionAddon.getAmmunition(new ItemBuilder(b).getStringTagData("ammo"));
			return ammo1 != null && ammo2 != null && ammo1.compareTo(ammo2) == 0;
		};
	}
}
