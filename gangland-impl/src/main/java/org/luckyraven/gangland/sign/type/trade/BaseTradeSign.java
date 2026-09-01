package org.luckyraven.gangland.sign.type.trade;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.sign.aspect.ItemTransferAspect.ItemSimilarityChecker;
import org.luckyraven.gangland.sign.type.Sign;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.ammo.Ammunition;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.types.WeaponType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public abstract class BaseTradeSign implements Sign {

	private final WeaponService     weaponService;
	private final AmmunitionManager ammunitionManager;

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
		Weapon template = weaponService.getWeapons()
		                               .values()
				.stream()
				.filter(w -> w.getName().equalsIgnoreCase(weaponName))
				.findFirst()
				.orElse(null);
		if (template == null) return null;

		UUID uuid;
		if (template.getCategory() == WeaponType.THROWABLE) {
			uuid = UUID.nameUUIDFromBytes(("throwable:" + template.getName()).getBytes(StandardCharsets.UTF_8));
		} else {
			uuid = UUID.randomUUID();
		}
		return template.copyWithUUID(uuid).buildItem();
	}

	protected ItemStack getAmmoItem(String ammoKey) {
		return ammunitionManager.getAmmunitionKeys()
				.stream()
				.filter(k -> k.equalsIgnoreCase(ammoKey))
				.map(ammunitionManager::getAmmunition)
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
			Ammunition ammo1 = ammunitionManager.getAmmunition(new ItemBuilder(a).getStringTagData(Ammunition.NBT_KEY));
			Ammunition ammo2 = ammunitionManager.getAmmunition(new ItemBuilder(b).getStringTagData(Ammunition.NBT_KEY));
			return ammo1 != null && ammo2 != null && ammo1.compareTo(ammo2) == 0;
		};
	}
}
